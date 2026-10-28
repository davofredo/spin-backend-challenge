# spin-backend-challenge

REST API that executes financial transactions (`CREDIT` / `DEBIT`) against an external provider,
persists the result and exposes a query endpoint.

The service does **not** manage balances or accounts: the provider validates funds and blocks, this
service only executes, records and exposes transactions.

## Stack

- Java 21, Spring Boot 3.5, Maven
- Spring Web / Validation / Data JPA, PostgreSQL + Flyway
- Resilience4j (timeout, retry, circuit breaker) around the provider client
- Spring `RestClient` (JDK `HttpClient`) as HTTP client
- Tests: JUnit 5, Mockito, AssertJ, WireMock, Testcontainers (PostgreSQL), JaCoCo

## Architecture

Ports & adapters, dependencies point inward (`infrastructure → application → domain`). The domain
has no Spring, JPA or HTTP imports, and the application layer depends on the
`TransactionProvider` / `TransactionRepository` **interfaces** only.

```
com.example.challenge
├── domain/model               Transaction, TransactionRequest, TransactionType, TransactionStatus, rules
├── domain/exception           business and validation exceptions
├── application/port/in        use cases (in progress)
├── application/port/out       TransactionProvider, TransactionRepository, ProviderResult
├── application/model          shared application types (search criteria, page result)
└── infrastructure
    ├── adapter/out/provider     HTTP provider client (Resilience4j retry + circuit breaker)
    ├── adapter/out/persistence  JPA entity, Spring Data repository, mapper
    └── config                   provider properties, RestClient and resilience beans
```

## Running locally

Start PostgreSQL and the provider stand-in (WireMock):

```bash
docker compose up -d
docker compose ps
```

The provider stub answers on `http://localhost:8081`:

```bash
curl -i -X POST http://localhost:8081/provider/v1/execute \
  -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN"}'
# 200 {"transactionId":"provider-txn-approved","status":"APPROVED","balance":1500.00,"executedAt":"..."}
```

Stub mappings live in [`wiremock/mappings`](./wiremock/mappings) and are mounted into the container.
Three stubs are provided: an approval, a rejection driven by `accountId == "acc-insufficient-funds"`
and an outage driven by `accountId == "acc-provider-down"`.

Then run the application:

```bash
./mvnw spring-boot:run
```

Configuration (all overridable by environment variable):

| Property | Env var | Default |
| --- | --- | --- |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/challenge` |
| `spring.datasource.username` | `DB_USERNAME` | `challenge` |
| `spring.datasource.password` | `DB_PASSWORD` | `challenge` |
| `server.port` | `SERVER_PORT` | `8080` |
| `provider.base-url` | `PROVIDER_BASE_URL` | `http://localhost:8081` |
| `provider.connect-timeout` | `PROVIDER_CONNECT_TIMEOUT` | `2s` |
| `provider.read-timeout` | `PROVIDER_READ_TIMEOUT` | `5s` |

The schema is owned by Flyway migrations (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto=validate` and never creates tables.

## Provider integration

Contract: `POST /provider/v1/execute` with `{ accountId, type, amount, currency }`.

| Provider response | `ProviderResult` | Persisted status |
| --- | --- | --- |
| `200 { transactionId, status: "APPROVED", balance, executedAt }` | `Approved` | `EXECUTED` |
| `4xx { status: "REJECTED", code, message }` | `Rejected(code, message)` | `REJECTED` |
| `5xx`, timeout, connection error, open circuit | `Unavailable(reason)` | `FAILED` |
| Malformed body / missing required fields | `Unavailable(reason)` | `FAILED` |

`ProviderResult` is a sealed interface, so the application layer reasons about these three outcomes
without knowing that HTTP exists. The adapter never throws for an unreachable provider.

Rejection fields are truncated to the column bounds before they leave the adapter
(`error_code` 64 chars, `error_message` 500 chars): an oversized provider answer must never turn a
rejection into a failed insert.

### Retry and circuit breaker

Both are instance-based (`resilience4j.retry.instances.provider` and
`resilience4j.circuitbreaker.instances.provider` in `application.yml`) and are applied in this order:

```
execute() → Retry → CircuitBreaker → HTTP call
```

**Retry wraps the circuit breaker.** Each attempt is a separate call recorded by the breaker, so one
logical request can feed several failures into the sliding window and the breaker reflects what the
provider is really doing. With the opposite order the breaker would see one event per logical
request, hiding repeated failures and staying closed while the provider is down. The trade-off is
that the breaker trips sooner, which is the safe direction when the outcome is unconfirmed.

Both decorations only react to `ProviderTransientException` (5xx, connection errors, timeouts). A 4xx
is returned as a value instead of being thrown, so it **cannot** match `retry-exceptions` or
`record-exceptions`: a rejection is never retried and never counts toward opening the circuit. When
the retries are exhausted or the circuit is open the adapter returns `Unavailable`.

### Retrying a DEBIT: known trade-off

A read timeout does not prove the provider did not execute the transaction. Retrying a `DEBIT` after
a timeout or a connection error can therefore execute it twice on the provider side, and the losing
side is our record of it.

This is accepted on purpose: the alternative (never retrying) turns a transient network blip into a
`FAILED` transaction that the provider may in fact have executed, which is worse for reconciliation.
The mitigations are (a) retries are limited to 3 attempts with exponential backoff, (b) a 4xx is
never retried, and (c) a `FAILED` transaction is stored with `errorCode = PROVIDER_UNAVAILABLE` so it
stays visible for reconciliation. Callers that need exactly-once semantics should send the optional
`Idempotency-Key` header, which makes the API replay the stored transaction instead of executing
again.

## Tests

```bash
./mvnw test                                        # unit, web, WireMock and Testcontainers tests
./mvnw verify                                      # + JaCoCo report in target/site/jacoco
./mvnw test -Dtest=ProviderHttpAdapterTest         # provider adapter only
```

`ProviderHttpAdapterTest` runs the real resilience4j configuration against a WireMock server and
covers: approval, rejection with provider code, 5xx, timeout, connection reset, malformed body,
retry-then-success, no retry on 4xx (and no breaker failure), circuit opening after repeated
failures, and truncation of oversized rejection fields. There is no H2 anywhere: persistence tests
run against PostgreSQL via Testcontainers, applying the same Flyway migrations as production.

## API

`POST /transactions` executes a transaction; `GET /transactions` queries them with
`accountId` / `status` / `type` / `page` / `limit` filters. The web adapter and the use cases are the
next stages; domain, persistence and provider adapters are already in place.

## Out of scope

Balance/account management, authentication/authorization, messaging (Kafka), a real provider
implementation and multi-currency support. Mentioned here only as future evolution.
