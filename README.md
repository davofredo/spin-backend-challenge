# spin-backend-challenge

API REST que ejecuta transacciones financieras (`CREDIT` / `DEBIT`) contra un proveedor externo,
persiste el resultado y expone un endpoint de consulta.

El servicio **no** administra saldos ni cuentas: el proveedor valida fondos y bloqueos, y este
servicio solo ejecuta, registra y expone transacciones.

## Stack

- Java 21, Spring Boot 3.5, Maven
- Spring Web / Validation / Data JPA, PostgreSQL + Flyway
- Resilience4j (timeout, retry, circuit breaker) alrededor del cliente del proveedor
- Spring `RestClient` (JDK `HttpClient`) como cliente HTTP
- Pruebas: JUnit 5, Mockito, AssertJ, WireMock, Testcontainers (PostgreSQL), JaCoCo

## Arquitectura

Puertos y adaptadores: las dependencias apuntan hacia adentro (`infrastructure → application →
domain`). El dominio no importa Spring, JPA ni HTTP, y la capa de aplicación depende únicamente de
las **interfaces** `TransactionProvider` / `TransactionRepository`.

```
com.example.challenge
├── domain/model               Transaction, TransactionRequest, TransactionType, TransactionStatus, reglas
├── domain/exception           excepciones de negocio y de validación
├── application/port/in        ExecuteTransactionUseCase, QueryTransactionsUseCase (+ command)
├── application/port/out       TransactionProvider, TransactionRepository, ProviderResult
├── application/model          tipos compartidos de la aplicación (criterios de búsqueda, resultado paginado)
├── application/service        implementaciones de casos de uso (idempotencia, reglas, llamada al proveedor, guardado)
└── infrastructure
    ├── adapter/in/web           controladores, DTOs de request/response, manejador de excepciones RFC 7807
    ├── adapter/out/provider     cliente HTTP del proveedor (retry + circuit breaker con Resilience4j)
    ├── adapter/out/persistence  entidad JPA, repositorio Spring Data, mapper
    └── config                   propiedades del proveedor, RestClient, beans de resiliencia y de Clock
```

## Ejecución local

Primero inicia PostgreSQL y el sustituto del proveedor (WireMock):

```bash
docker compose up -d
docker compose ps
```

El stub del proveedor responde en `http://localhost:8081`:

```bash
curl -i -X POST http://localhost:8081/provider/v1/execute \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN"}'
# 200 {"transactionId":"provider-txn-approved","status":"APPROVED","balance":1500.00,"executedAt":"..."}
```

Los mappings de los stubs viven en [`wiremock/mappings`](./wiremock/mappings) y se montan dentro del
contenedor. Se incluyen cuatro stubs, todos indexados por `accountId`:

| `accountId` | Comportamiento del stub | Resultado |
| --- | --- | --- |
| cualquier otro | `200 APPROVED` | `EXECUTED` |
| `acc-insufficient-funds` | `422 { status: "REJECTED", code: "INSUFFICIENT_FUNDS" }` | `REJECTED` |
| `acc-provider-down` | `503` | `FAILED` |
| `acc-timeout` | `200 APPROVED` tras un retardo de 6 s (mayor que `provider.read-timeout`) | `FAILED` |

El stub `acc-timeout` ejercita el read timeout de extremo a extremo: cada intento se corta después de
`provider.read-timeout`, se reintenta `max-attempts` veces con backoff y finalmente se persiste como
`FAILED` con `PROVIDER_UNAVAILABLE`. Hay que esperar aproximadamente 16 s antes de la respuesta `502`.
WireMock también admite fallos por stub, como `"fault": "EMPTY_RESPONSE"`, si prefieres simular una
conexión cortada.

Después ejecuta la aplicación:

```bash
./mvnw spring-boot:run
```

Configuración (todo se puede sobrescribir con variables de entorno):

| Propiedad | Variable de entorno | Valor por defecto |
| --- | --- | --- |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/challenge` |
| `spring.datasource.username` | `DB_USERNAME` | `challenge` |
| `spring.datasource.password` | `DB_PASSWORD` | `challenge` |
| `server.port` | `SERVER_PORT` | `8080` |
| `provider.base-url` | `PROVIDER_BASE_URL` | `http://localhost:8081` |
| `provider.connect-timeout` | `PROVIDER_CONNECT_TIMEOUT` | `2s` |
| `provider.read-timeout` | `PROVIDER_READ_TIMEOUT` | `5s` |

El esquema es propiedad de las migraciones de Flyway (`src/main/resources/db/migration`); Hibernate
corre con `ddl-auto=validate` y nunca crea tablas.

## Integración con el proveedor

Contrato: `POST /provider/v1/execute` con `{ accountId, type, amount, currency }`.

| Respuesta del proveedor | `ProviderResult` | Estado persistido |
| --- | --- | --- |
| `200 { transactionId, status: "APPROVED", balance, executedAt }` | `Approved` | `EXECUTED` |
| `4xx { status: "REJECTED", code, message }` | `Rejected(code, message)` | `REJECTED` |
| `5xx`, timeout, error de conexión, circuito abierto | `Unavailable(reason)` | `FAILED` |
| Cualquier otro 2xx/4xx, cuerpo malformado o campos obligatorios ausentes | `Unavailable(reason)` | `FAILED` |

`ProviderResult` es una interfaz sellada, así que la capa de aplicación razona sobre estos tres
resultados sin saber que existe HTTP. El adaptador nunca lanza una excepción porque el proveedor no
sea alcanzable.

El adaptador es deliberadamente estricto, porque un cuerpo que no corresponde al contrato no tiene un
significado de negocio confiable:

- un 2xx es una aprobación **solo** cuando `status` es exactamente `APPROVED`, `transactionId` está
  presente, `balance` está presente y su escala es como máximo 2 (`1500.755` no se puede guardar como
  `NUMERIC(19,2)`);
- un 4xx es un rechazo **solo** cuando `status` es exactamente `REJECTED` y `code` está presente.
  Todo lo demás —`401`, `403`, `404`, `429`, un cuerpo que no se puede parsear, un `{"error":"Not
  Found"}` estilo gateway o un envelope `{"status":"ERROR","code":...}`— se convierte en
  `Unavailable`, no en `Rejected`, para no inventar un error de negocio a partir de una respuesta que
  no estaba contestando el contrato;
- una violación del contrato se registra en WARN nombrando únicamente el campo que falla (`status`,
  `transactionId`, `balance`) o el estado HTTP; el payload nunca se registra. No se reintenta:
  repetir una llamada que el proveedor ya contestó no puede arreglar un problema de payload, y
  reintentar un `DEBIT` arriesga ejecutarlo dos veces.

Los campos de rechazo se truncan a los límites de la columna antes de salir del adaptador
(`error_code` 64 caracteres, `error_message` 500 caracteres): una respuesta demasiado grande del
proveedor nunca debe convertir un rechazo en un fallo de inserción.

### Reintentos y circuit breaker

Ambos son por instancia (`resilience4j.retry.instances.provider` y
`resilience4j.circuitbreaker.instances.provider` en `application.yml`) y se aplican en este orden:

```
execute() → Retry → CircuitBreaker → HTTP call
```

**Retry envuelve al circuit breaker.** Cada intento es una llamada separada que registra el breaker,
así que una sola petición lógica puede aportar varios fallos a la ventana deslizante y el breaker
refleja lo que el proveedor está haciendo realmente. Con el orden inverso el breaker vería un evento
por petición lógica, ocultaría los fallos repetidos y se mantendría cerrado mientras el proveedor está
caído. El precio es que el breaker se abre antes, que es la dirección segura cuando el resultado no
está confirmado.

Ambas decoraciones reaccionan únicamente a `ProviderTransientException` (5xx, errores de conexión,
timeouts). Un 4xx se devuelve como valor en lugar de lanzarse, así que **no puede** coincidir con
`retry-exceptions` ni con `record-exceptions`: un rechazo nunca se reintenta y nunca cuenta para abrir
el circuito, y esto también aplica a un 4xx inesperado (`401`/`404`/cuerpo de gateway), que sigue
siendo una respuesta y no una caída. Cuando se agotan los reintentos o el circuito está abierto, el
adaptador devuelve `Unavailable`.

Ambas instancias publican sus eventos en el log, registrados una sola vez en `ProviderConfig`:

- retry: WARN por intento, p. ej. `Provider call failed, retrying: attempt 1` (solo el número de
  intento, sin payload);
- circuit breaker: WARN cuando el estado destino es `OPEN`, INFO para cualquier otra transición.

### Reintentar un DEBIT: trade-off conocido

Un read timeout no demuestra que el proveedor no haya ejecutado la transacción. Reintentar un `DEBIT`
después de un timeout o de un error de conexión puede, por tanto, ejecutarlo dos veces del lado del
proveedor, y el lado que pierde es nuestro registro.

Esto se acepta a propósito: la alternativa (no reintentar nunca) convierte un fallo de red transitorio
en una transacción `FAILED` que el proveedor quizá sí ejecutó, lo cual es peor para la conciliación.
Las mitigaciones son (a) los reintentos se limitan a 3 intentos con backoff exponencial, (b) un 4xx
nunca se reintenta y (c) una transacción `FAILED` se guarda con `errorCode = PROVIDER_UNAVAILABLE`
para que siga visible para conciliación. Los clientes que necesitan semántica exactly-once deben
enviar la cabecera opcional `Idempotency-Key`, que hace que la API devuelva la transacción almacenada
en lugar de volver a ejecutarla.

## Pruebas

```bash
./mvnw test                                        # pruebas de unidad, web, WireMock y Testcontainers
./mvnw verify                                      # + reporte JaCoCo en target/site/jacoco
./mvnw test -Dtest=ProviderHttpAdapterTest         # solo el adaptador del proveedor
```

`ProviderHttpAdapterTest` ejecuta la configuración real de resilience4j contra un servidor WireMock y
cubre: aprobación, rechazo con código del proveedor, 5xx, timeout, conexión reiniciada, cuerpo
malformado, violaciones del contrato en la ruta 2xx (`status` `PENDING`/`null`, `transactionId`
ausente, `balance` ausente, `balance` con 3 decimales), 4xx inesperados (`401` y `404` con cuerpos
estilo gateway, un envelope `status: "ERROR"`, un rechazo sin `code`), request nulo, retry que termina
en éxito, sin retry en 4xx (y sin fallo registrado en el breaker), apertura del circuito tras varios
fallos y truncado de campos de rechazo demasiado grandes. No hay H2 en ningún lugar: las pruebas de
persistencia corren contra PostgreSQL vía Testcontainers, aplicando las mismas migraciones de Flyway
que producción. El timing del backoff exponencial no se asserta, así que la suite se mantiene rápida y
no depende del scheduler.

`ExecuteTransactionServiceTest` y `QueryTransactionsServiceTest` cubren la capa de aplicación con
Mockito y un `Clock` fijo: cada violación de regla de negocio (verificando que el proveedor nunca se
llama y que no se guarda nada), los tres resultados del proveedor con sus campos persistidos exactos,
una repetición idempotente y la carrera de clave duplicada donde `save` pierde contra una petición
concurrente.

`ExecuteTransactionEndpointTest` y `QueryTransactionsEndpointTest` son slices `@WebMvcTest` con los dos
casos de uso mockeados. Cubren la petición válida y su mapeo al command del caso de uso, cada fallo de
bean validation (incluyendo un `Idempotency-Key` demasiado largo, un cuerpo ilegible y un cuerpo con un
enum desconocido), las violaciones de reglas de negocio respondidas como `422` con el código de la
regla, `201` para `EXECUTED` y `REJECTED` frente a `502` para `FAILED`, los filtros y defaults de
paginación de la consulta, `limit` mayor que `100`, `page` negativa, valores desconocidos de
`status`/`type` y la forma del `ProblemDetail` en cada error.

## API

`POST /transactions` ejecuta una transacción y `GET /transactions` las consulta con los filtros
`accountId` / `status` / `type` / `page` / `limit`. Ambos endpoints están implementados de extremo a
extremo a través de los casos de uso, por lo que el servicio está completo.

```bash
curl -i -X POST http://localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 4c1b2f60-1e35-4d5a-9a1e-2b7c9d0e5f11' \
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN","description":"Transferencia recibida"}'
# 201 {"id":"...","status":"EXECUTED","providerTransactionId":"...","balanceAfter":1500.00,...}

curl -i 'http://localhost:8080/transactions?accountId=acc-123456&status=EXECUTED&page=0&limit=20'
# 200 {"items":[...],"page":0,"limit":20,"totalElements":1,"totalPages":1}
```

Una transacción `EXECUTED` o `REJECTED` se responde con `201` y su representación almacenada. Cuando el
resultado del proveedor es desconocido, la transacción se guarda igualmente como `FAILED`, pero la
respuesta es `502` con un `ProblemDetail` genérico: el cliente no puede saber si el proveedor la
ejecutó, así que simular que la llamada tuvo éxito sería engañoso. Las violaciones de reglas de negocio
responden `422` con una propiedad `code` (`AMOUNT_TOO_LOW`, `DEBIT_LIMIT_EXCEEDED`,
`UNSUPPORTED_CURRENCY`) y los fallos de validación responden `400`. OpenAPI/Swagger UI está disponible
en `/swagger-ui.html`.

## Uso de IA en este proyecto

El código se desarrolló con asistencia de IA, con un reparto explícito de roles: la IA propone, el
autor decide y verifica. Ningún cambio se commitea sin que el autor pueda explicar cada línea
(AGENTS.md §10).

### Archivos de contexto

| Archivo | Para quién | Para qué sirve |
| --- | --- | --- |
| [`AGENTS.md`](./AGENTS.md) | agente programador **y** agente revisor | Fuente de verdad del proyecto: stack, arquitectura de puertos y adaptadores, reglas de negocio del dominio, estilo, estrategia de pruebas y flujo de trabajo. Se lee completo antes de generar o modificar código. |
| [`docs/ai/REVIEW.md`](./docs/ai/REVIEW.md) | agente revisor | Guía de revisión de código: alcance de la revisión, checklist de cumplimiento del reto, arquitectura, dinero y concurrencia, integración y resiliencia, calidad de las pruebas y formato de salida de los hallazgos. |

No se usan **Skills**: el alcance del proyecto es un único servicio con una arquitectura estable, y dos
archivos de contexto bien mantenidos cubren lo que aportaría un conjunto de skills, sin la carga de
mantenerlos sincronizados con el código. Si el proyecto creciera a varios módulos o lo retomaran otros
equipos, ese sería el momento de reconsiderarlo.

### Modelos y para qué se usó cada uno

| Modelo | Rol | Por qué |
| --- | --- | --- |
| Claude Sonnet 5.5 | Planificación y mentoría (chat) | Buenas habilidades de diseño de software. No es complaciente y tiende a defender su punto de vista, lo que obliga a argumentar cada decisión de diseño en lugar de aceptar la primera respuesta. |
| DeepSeek 4.1 Flash | Programación | Más económico y adecuado para tareas largas sin quedarse sin tokens a mitad de una etapa (adaptador del proveedor, capa de aplicación, capa web). |
| Gemini 3.8 Flash | Revisión de código | Buen razonamiento y buena retroalimentación cuando el objetivo y las directivas están claramente definidos, de ahí el papel de `REVIEW.md` como instrucción de revisión. |

### Cómo se usó cada rol

1. **Planificación y mentoría (Claude Sonnet 5.5).** Se discutieron las decisiones que sostienen el
   diseño: puertos y adaptadores, el orden de retry y circuit breaker, el modelo de `Transaction`
   construido solo mediante factorías (`executed` / `rejected` / `failed`) y el trade-off de reintentar
   un `DEBIT`. Las conclusiones se escribieron en `AGENTS.md` como contrato, no como sugerencia.
2. **Programación (DeepSeek 4.1 Flash).** Implementación por etapas verticales —proveedor, capa de
   aplicación, capa web—, cada una cerrada con `./mvnw clean verify` en verde. El agente recibía
   `AGENTS.md` y un alcance acotado ("solo la capa de aplicación", "solo el adaptador web"), sin tocar
   las capas ya cerradas.
3. **Revisión de código (Gemini 3.8 Flash).** Ejecutada con `docs/ai/REVIEW.md` como directiva. Los
   hallazgos se clasificaron en bloqueantes, importantes y menores, y se corrigieron en un commit
   propio; por eso el historial separa la funcionalidad de las correcciones surgidas de la revisión.
4. **Verificación humana.** Las reglas de negocio en sus límites (1.00 vs 1.01, 10,000.00 vs
   10,000.01), el contrato del proveedor y cada decisión de resiliencia se validaron ejecutando la
   aplicación contra PostgreSQL y WireMock, no solo leyendo el código. Las pruebas generadas por IA se
   leen y se ejecutan: que pasen no es evidencia por sí mismo si no assertan el comportamiento
   esperado.

## Fuera de alcance

Administración de saldos y cuentas, autenticación/autorización, mensajería (Kafka), una implementación
real del proveedor y soporte multi-divisa. Se mencionan aquí solo como evolución futura.
