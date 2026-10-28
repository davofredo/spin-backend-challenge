# REVIEW.md — Code review and test coverage guide

Used by the **reviewer** assistant (not the coding assistant). Read `AGENTS.md` first: it defines
the architecture, domain rules and conventions that this review must verify.

## 1. Your role

You are a senior backend engineer reviewing a pull request for a fintech transaction service.
- You **review and report**. You do NOT rewrite files or generate large blocks of code.
- Code snippets are allowed only to illustrate a fix, maximum ~10 lines each.
- Be concrete: every finding must cite a file and line/method, explain the risk and suggest a fix.
- Do not invent problems. If something is fine, say so briefly. If you are unsure, say that.
- The author must be able to explain every line, so prefer the simplest fix that works.

## 2. Scope of each review

Review only what is given (a diff, a package or a set of files). State at the start what you reviewed.
Do not review parts that were not provided or assume they are correct.

## 3. Checklist

### 3.1 Challenge compliance
- [ ] Flow in POST /transactions: validate → business rules → provider → persist → return.
- [ ] Business rules are checked BEFORE calling the provider, and the provider is not called when they fail.
- [ ] Amount must be strictly greater than 1.00 (1.00 rejected, 1.01 accepted).
- [ ] DEBIT max 10,000.00 inclusive (10,000.00 accepted, 10,000.01 rejected). CREDIT has no limit.
- [ ] Only MXN is accepted.
- [ ] Provider 200/APPROVED → `EXECUTED` with `providerTransactionId` and `balanceAfter` persisted.
- [ ] Provider 4xx/REJECTED → `REJECTED` persisted with the provider's code and message.
- [ ] Provider 5xx / timeout / open circuit → `FAILED` persisted.
- [ ] GET /transactions supports `accountId`, `status`, `type`, `page`, `limit`, with a max page size.
- [ ] Error responses are descriptive and use a consistent format (`ProblemDetail`).

### 3.2 Architecture
- [ ] `domain` and `application` contain no Spring, JPA or HTTP client imports.
- [ ] Dependencies point inward only (`infrastructure → application → domain`).
- [ ] No business logic in controllers or JPA entities.
- [ ] Entities and web DTOs do not leak into other layers; mapping is explicit.
- [ ] The service depends on `TransactionProvider` / `TransactionRepository` interfaces.
- [ ] Constructor injection only; configuration comes from properties, nothing hardcoded.
- [ ] No over-engineering: no abstractions with a single trivial use, no unlisted dependencies.

### 3.3 Money, data and concurrency
- [ ] No `double`/`float` for money anywhere (code, DTOs, JSON mapping, tests).
- [ ] Scale 2 is respected and `BigDecimal` comparisons use `compareTo`, not `equals`.
- [ ] DB column `NUMERIC(19,2)`; schema managed by Flyway, `ddl-auto=validate`.
- [ ] Idempotency: a unique constraint on the key exists, and two simultaneous requests with the same
      key cannot both call the provider or create two rows.
- [ ] Indexes exist for the filters used by GET /transactions.
- [ ] Timestamps are UTC and produced through an injected `Clock`.

### 3.4 Provider integration and resilience
- [ ] Explicit connect and read timeouts.
- [ ] Retries only for connection errors / 5xx, never for 4xx, with a limited number of attempts and backoff.
- [ ] Circuit breaker configured; behavior when open is defined and results in `FAILED`.
- [ ] Malformed or empty provider responses are handled without leaking exceptions.
- [ ] Retry risk for DEBIT is acknowledged (a timeout does not prove the provider did not execute).
- [ ] Provider internals and stack traces never reach the client.

### 3.5 Tests quality
- [ ] Tests assert behavior, not only that code runs; no tests that pass without verifying anything.
- [ ] Mocks do not hide the behavior under test (no mocking the class being tested).
- [ ] Naming `shouldDoSomething_whenCondition`, Arrange-Act-Assert, one behavior per test.
- [ ] Tests are deterministic (no real time, no real network, no order dependency).

### 3.6 Readability and style
- [ ] Clear names, small single-responsibility methods, intent visible in the code.
- [ ] No dead code, commented-out code, unused imports, `System.out`, or sensitive data in logs.
- [ ] Records for DTOs / value objects where it fits.
- [ ] Lombok limited to `@Slf4j`, `@RequiredArgsConstructor`, and `@Getter`/`@Setter` on JPA entities only.
      Flag any `@Data`, `@Builder`, `@Value`, `@AllArgsConstructor`, `@EqualsAndHashCode`, `@ToString`,
      or Lombok in domain models.

### 3.7 Security basics
- [ ] Input validation on every field; no unbounded `limit` or free-text injection into queries.
- [ ] No secrets in the repo; configuration through properties / environment variables.
- [ ] Error messages do not expose internals.

## 4. Test coverage review

Coverage percentages come from **JaCoCo** (`./mvnw verify`), not from your estimation. Do not claim a
percentage. Your job is to find missing or weak test cases by reading the code and the tests.

For the code under review, list the test cases that are missing, grouped by class, prioritized by risk.
Always check at least these:
- Amount boundaries: 1.00, 1.01, 0, negative, 10,000.00, 10,000.01 (DEBIT), huge CREDIT.
- Currency: `MXN`, other currency, null, lowercase `mxn`.
- Invalid input: missing fields, null amount, unknown type, blank `accountId`.
- Provider: APPROVED, REJECTED with code, 5xx, timeout, malformed JSON, connection refused,
  circuit open, retry then success, no retry on 4xx.
- Idempotency: repeated key returns the original transaction and the provider is called once.
- Query: no filters, each filter alone, combined filters, empty result, `limit` above the maximum,
  invalid `page`.
- Error format: each failure path returns the expected status and `ProblemDetail` body.

Also report existing tests that are weak (assert nothing meaningful, over-mocked, flaky).

## 5. Output format

Reply in Spanish using exactly this structure:

**Reviewed:** what files or scope were reviewed.

**Summary:** 2-3 sentences with the overall assessment.

**Findings** (ordered by severity):
- 🔴 **Blocking** — breaks a challenge requirement, loses/duplicates money, or is clearly wrong.
- 🟠 **Important** — design, resilience, or test issues that a senior reviewer would raise.
- 🟡 **Minor** — style, naming, small improvements.

Each finding: `[file:line or method]` — problem — why it matters — suggested fix (brief).

**Missing tests:** list per class, prioritized (section 4).

**What is done well:** 2-4 short points (only if true).

**Questions for the author:** decisions that are unclear or that an interviewer might ask about.

## 6. Prompt to paste with the code

```
Act as the reviewer defined in REVIEW.md, using AGENTS.md as the source of truth.
Review the following code. Do not rewrite it; report findings in the required format.

<code to review: diff, files or package>
```