# spin-backend-challenge

API REST que ejecuta transacciones financieras (`CREDIT` / `DEBIT`) contra un proveedor externo,
persiste el resultado y expone un endpoint de consulta.

El servicio **no** administra saldos ni cuentas: el proveedor valida fondos y bloqueos, y este
servicio solo ejecuta, registra y expone transacciones.

## Requisitos para ejecutar

- Java 21 (./mvnw descarga la versión de Maven, no hace falta instalarla)
- Docker: Se requiere para docker compose (Contiene PostgreSQL y WireMock)

## Reglas de negocio y estados

Las reglas se validan en el servicio antes de llamar al proveedor. Si alguna falla, no se llama al proveedor, no se persiste nada y responde 422 con el código de la regla.

### Códigos de regla:

- `AMOUNT_TOO_LOW`: El monto debe ser mayor que 1.00
- `DEBIT_LIMIT_EXCEEDED`: El monto del débito no debe exceder 10,000.00 
- `UNSUPPORTED_CURRENCY`: Sólo se acepta la moneda MXN

### Estados de la transacción:

| Estado | Significado | `errorCode` / `errorMessage` | Respuesta HTTP |
| --- | --- | --- | --- |
| `EXECUTED` | El proveedor aprobó la transacción | nulos | `201` |
| `REJECTED` | El proveedor respondió que no (por ejemplo `INSUFFICIENT_FUNDS`) | código y mensaje del proveedor | `201` |
| `FAILED` | El resultado en el proveedor es desconocido (caído, timeout, circuito abierto, respuesta mal formada) | `PROVIDER_UNAVAILABLE` y mensaje genérico | `502` |

Decisiones de diseño sobre las respuestas:

- **REJECTED responde `201`**: la transacción se creó y quedó registrada; el rechazo proviene del proveedor, no es un error de la petición (Nuestra API acepta la respuesta, pero el proveedor la rechaza).
- **FAILED responde `502`**: el servicio no pudo completar la operación y el cliente no puede saber si
  el proveedor la ejecutó, así que no se simula un éxito. La transacción se guarda para fines de auditoría.
- **Los montos** se manejan como `BigDecimal` con escala 2 (nunca `double`). Un monto con más de dos
  decimales se rechaza con `400`; no se redondea en silencio.

---

## Idempotencia

`POST /transactions` acepta la cabecera opcional `Idempotency-Key` (máximo 100 caracteres). Si se repite la
misma clave, la API devuelve la transacción ya almacenada y **no** vuelve a llamar al proveedor, incluso
si el primer resultado fue un rechazo.

Antes de ejecutar se consulta la clave; como red de seguridad, la base de datos tiene una
restricción `UNIQUE` (`uq_transactions_idempotency_key`) y el repositorio traduce su violación a una
excepción propia, tras lo cual el servicio devuelve la transacción existente.

Limitaciones conocidas:

- **Carrera entre solicitudes simultáneas con la misma clave**: ambas pueden llegar al proveedor antes de
  que una falle al guardar. La base de datos impide dos registros, pero no dos llamadas al proveedor. Una
  mejora sería reservar la clave guardando primero una transacción pendiente.
- **Repetición tras un `FAILED`**: devuelve el `FAILED` guardado. Como el resultado en el proveedor es
  desconocido, no se reintenta a ciegas (podría duplicarse un débito); para reintentar se usa otra clave.
- **Misma clave con datos distintos**: se devuelve la transacción original sin avisar. Sería preferible
  responder con un error de conflicto; queda como mejora pendiente.
- **No protege los reintentos internos hacia el proveedor.** La clave evita que un reintento del *cliente*
  ejecute dos veces la operación en este servicio. El riesgo de reintentar un `DEBIT` hacia el proveedor
  existe aunque el cliente haya enviado la clave, y solo se resolvería si el proveedor aceptara una clave
  de idempotencia propia (No se contempla en el contrato). Por simplicidad no se manejó esta situación en los re-tries, por lo que se dejó como una mejora futura.

---

## Consulta y paginación

`GET /transactions` acepta `accountId`, `status`, `type`, `page` y `limit`. La página es **base 0**, el
límite por defecto es 20 y el máximo 100; un valor fuera de rango o un estatus o type inválido responde
`400`. Un `accountId` vacío se ignora. El orden es por `createdAt` descendente con desempate por `id`, para
que la paginación sea estable.

Índices: `(account_id, created_at DESC)` y `(created_at DESC)`. No se
indexan `status` ni `type` para evitar encarecer las escrituras. Los filtros son opcionales
y combinables.

Trade-off: la respuesta incluye `totalElements`, esto puede resultar en un alto costo con volúmenes muy grandes de datos. Con millones de transacciones diarias podría ser necesario eliminar la visibilidad del total de elementos.

---

## Decisiones de diseño

- **Puertos y adaptadores**: el dominio y la aplicación no conocen Spring, JPA ni HTTP. El proveedor y la
  persistencia están detrás de interfaces, lo que facilita probarlos y cambiarlos.
- **Sin `@Transactional` alrededor del caso de uso**: no se mantiene una conexión de base de datos abierta
  durante la llamada HTTP al proveedor.
- **Reglas de negocio separadas del modelo**: son políticas que pueden cambiar y no deben impedir
  reconstruir datos históricos. Hoy son constantes en `TransactionRules`; Si más adelante se requiriera, podrían agregarse parámetros de configuración.
- **Errores**: formato `ProblemDetail` (RFC 7807). Datos inválidos `400`, reglas de negocio `422`, proveedor
  no disponible `502`, y errores inesperados `500` sin detalles internos.
- **Migraciones con Flyway** y `ddl-auto=validate`; pruebas de persistencia con PostgreSQL real
  (Testcontainers), sin H2 para evitar problemas de compatibilidad entre pruebas locales/unitarias y el servidor de producción.

---

## Estrategia de pruebas (versión corta)

- **Dominio y servicios**: pruebas unitarias con Mockito y un reloj fijo, incluyendo los límites de las
  reglas (1.00/1.01, 10,000.00/10,000.01).
- **Capa web**: `@WebMvcTest` con los casos de uso mockeados (validaciones, códigos HTTP y forma del error).
- **Cliente del proveedor**: pruebas con WireMock (aprobación, rechazo, 5xx, timeout, respuestas mal
  formadas, reintentos y circuit breaker).
- **Persistencia**: Testcontainers con PostgreSQL y las mismas migraciones de producción.

---

## Uso de IA

Con la intención de generar el proyecto en el tiempo estipulado me apoyé de herramientas de IA
durante todo el proyecto, desde la planificación hasta el desarrollo y el code review.
- **Planificación y dudas de diseño** (Claude): decisiones de arquitectura, idempotencia, resiliencia y estrategia de pruebas, que se plasmaron en `AGENTS.md`.
- **Generación de código** (DeepSeek): implementación por etapas, con `AGENTS.md` como contexto fijo y un alcance acotado en cada etapa.
- **Revisión de código** (Gemini): revisión por etapas siguiendo `docs/ai/REVIEW.md`. Yo decidí qué
hallazgos aplicar y cuáles descartar, en ocasiones consultando con Claude si tenía dudas o no entendía del todo el feedback.
- **Traducción del README** al español (DeepSeek). Solicité a Deepseek que tradujera el README al español y agregara una sección sobre el uso de IA como medida rápida cuando surgió algo que comprometió la fecha de entrega. La intención era cubrir el requerimiento de documentación en caso de que se rechazara mi solicitud de una prórroga.

Revisé el código generado y lo ejecuté contra PostgreSQL y WireMock (pruebas manuales con Bruno).
Para facilitar la validación de la API incluyo el export de mi colección de pruebas en `docs\bruno\spin-backend-challenge.yml`.

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

## Persistencia 

Se usa **PostgreSQL** como base de datos, con el esquema versionado mediante **Flyway** (migraciones SQL)
y Hibernate en modo `validate`, de modo que solo verifica que las entidades coinciden con el esquema.
Los tests de persistencia se ejecutan contra PostgreSQL real con **Testcontainers**, aplicando las mismas
migraciones que en producción.

### Por qué PostgreSQL

- **Integridad en la base de datos.** La idempotencia depende de una restricción `UNIQUE` sobre
  `idempotency_key`: aunque dos solicitudes con la misma clave lleguen al mismo tiempo, la base de datos
  impide que se creen dos registros. Es una garantía más simple y probada que implementarla en la aplicación.
- **Exactitud numérica.** Los montos se guardan como `NUMERIC(19,2)`, que corresponde directamente con
  `BigDecimal` en Java y evita errores de redondeo propios de los tipos de coma flotante.
- **Consistencia fuerte.** Para un sistema que maneja dinero, se prefiere tomar ventaja de las garantías transaccionales incluidas en PostgreSQL que construirlas desde la aplicación.
- **Consultas con filtros y paginación.** Los filtros por `accountId`, `status` y `type` se apoyan en índices compuestos, por ejemplo `(account_id, created_at DESC)`.
- **Posible crecimiento relacional.** Hoy el servicio solo registra transacciones, pero un sistema real
  se conecta con cuentas, conciliación, reportes y auditoría, donde el modelo relacional es el terreno natural.

### Alternativa considerada: MongoDB

Dado que hoy el modelo es una sola colección conceptual, similar a un registro de transacciones,
MongoDB habría sido una opción válida: un documento por transacción, índices equivalentes y un esquema
más flexible, sin necesidad de migraciones. Se eligió PostgreSQL porque, para dinero, pesan más las
restricciones declarativas, los tipos exactos y la consistencia fuerte antes que la flexibilidad del esquema.
La diferencia entre ambas opciones en este caso es pequeña, y ninguna es la única respuesta correcta.

### La decisión es reversible

La persistencia está detrás del puerto `TransactionRepository`. El dominio y los casos de uso no conocen
JPA ni SQL, de modo que cambiar de motor (por ejemplo, a MongoDB) consistiría en escribir otro adapter
de salida sin modificar la lógica de negocio.

## Ejecución local

Primero inicia PostgreSQL y el sustituto del proveedor (WireMock):

```bash
docker compose up -d
docker compose ps
```

El proveedor responde en `http://localhost:8081`:

Después ejecuta la aplicación:

```bash
./mvnw spring-boot:run
```

El esquema de base de datos es administrado a través de migraciones de Flyway (`src/main/resources/db/migration`); Hibernate corre con `ddl-auto=validate` y nunca crea tablas.

## Integración con el proveedor

Contrato: `POST /provider/v1/execute` con `{ accountId, type, amount, currency }`.

| Respuesta del proveedor | `ProviderResult` | Estado persistido |
| --- | --- | --- |
| `200 { transactionId, status: "APPROVED", balance, executedAt }` | `Approved` | `EXECUTED` |
| `4xx { status: "REJECTED", code, message }` | `Rejected(code, message)` | `REJECTED` |
| `5xx`, timeout, error de conexión, circuito abierto | `Unavailable(reason)` | `FAILED` |
| Cualquier otro 2xx/4xx, cuerpo malformado o campos obligatorios ausentes | `Unavailable(reason)` | `FAILED` |

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

## Pruebas

```bash
./mvnw test                                        # pruebas de unidad, web, WireMock y Testcontainers
./mvnw verify                                      # + reporte JaCoCo en target/site/jacoco
./mvnw test -Dtest=ProviderHttpAdapterTest         # solo el adaptador del proveedor
```

## Fuera de alcance

Administración de saldos y cuentas, autenticación/autorización, mensajería (Kafka), una implementación
real del proveedor, soporte multi-divisa, API Docs. Se mencionan aquí solo como evolución futura.
