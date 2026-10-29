package com.example.challenge.infrastructure.adapter.out.provider;

import com.example.challenge.application.port.out.ProviderResult;
import com.example.challenge.application.port.out.TransactionProvider;
import com.example.challenge.domain.model.TransactionRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * HTTP adapter for {@code POST /provider/v1/execute}.
 * <p>
 * <b>Retry wraps the circuit breaker.</b> Each retry attempt is a separate call recorded by the
 * breaker, so a logical request with three attempts feeds up to three failures into the sliding
 * window. The alternative (breaker outside the retry) would let a single logical request produce one
 * breaker event, which hides repeated attempts from the breaker and lets it stay closed while the
 * provider is failing. The trade-off is that the breaker trips sooner, which is the safer behavior
 * for an unconfirmed outcome.
 * <p>
 * A 4xx is returned as {@link ProviderResult.Rejected} instead of throwing, so it never matches the
 * configured {@code retry-exceptions} nor the {@code record-exceptions}, which is what keeps a
 * rejection out of both the retry and the failure statistics. 5xx, connection errors and timeouts
 * throw {@link ProviderTransientException}.
 * <p>
 * A 2xx is an approval only when {@code status} is {@code APPROVED}, {@code transactionId} is
 * present and {@code balance} is present with at most two decimals; a 4xx is a rejection only when
 * {@code status} is {@code REJECTED} and {@code code} is present. Anything else the provider answers
 * (401/403/404/429, gateway bodies, {@code PENDING}, a missing field) violates the contract and is
 * reported as {@link ProviderResult.Unavailable} with a generic reason, logged at WARN naming only
 * the offending field: repeating a call the provider did answer cannot fix a contract violation, so
 * nothing is retried and the payload is never logged.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProviderHttpAdapter implements TransactionProvider {

    /** Matches {@code transactions.error_code VARCHAR(64)}. */
    static final int MAX_ERROR_CODE_LENGTH = 64;

    /** Matches {@code transactions.error_message VARCHAR(500)}. */
    static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    /** Money is stored as {@code NUMERIC(19,2)}; a finer scale cannot be persisted faithfully. */
    static final int MAX_MONEY_SCALE = 2;

    private static final String EXECUTE_PATH = "/provider/v1/execute";
    private static final String MALFORMED_RESPONSE = "Malformed provider response";
    private static final String UNEXPECTED_4XX_RESPONSE = "Unexpected provider 4xx response";
    private static final String UNEXPECTED_STATUS = "Unexpected provider status";
    private static final String APPROVED_STATUS = "APPROVED";
    private static final String REJECTED_STATUS = "REJECTED";

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    @Override
    public ProviderResult execute(TransactionRequest request) {
        Objects.requireNonNull(request, "request");

        Supplier<ProviderResult> guardedCall = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> callProvider(request)));
        try {
            return guardedCall.get();
        } catch (CallNotPermittedException ex) {
            log.warn("Provider circuit is open; transaction outcome is unknown");
            return ProviderResult.unavailable("Circuit is open");
        } catch (ProviderTransientException ex) {
            log.warn("Provider call failed after retries: {}", ex.getMessage());
            return ProviderResult.unavailable(ex.getMessage());
        }
    }

    private ProviderResult callProvider(TransactionRequest request) {
        try {
            return restClient.post()
                    .uri(EXECUTE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new ExecuteRequest(request.accountId(), request.type().name(),
                            request.amount(), request.currency()))
                    .exchange((httpRequest, httpResponse) -> mapResponse(httpResponse));
        } catch (ResourceAccessException ex) {
            throw new ProviderTransientException("Provider connection failed or timed out", ex);
        } catch (RestClientException | HttpMessageConversionException ex) {
            log.warn("Provider response could not be read: {}", ex.getClass().getSimpleName());
            return ProviderResult.unavailable(MALFORMED_RESPONSE);
        }
    }

    private ProviderResult mapResponse(ConvertibleClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is2xxSuccessful()) {
            return approved(response.bodyTo(ExecuteResponse.class));
        }
        if (status.is4xxClientError()) {
            return rejected(status.value(), response);
        }
        if (status.is5xxServerError()) {
            throw new ProviderTransientException("Provider responded with status " + status.value());
        }
        return ProviderResult.unavailable(UNEXPECTED_STATUS + " " + status.value());
    }

    private ProviderResult approved(ExecuteResponse response) {
        if (response == null) {
            return malformedResponse("body", "was empty");
        }
        if (!APPROVED_STATUS.equals(response.status())) {
            return malformedResponse("status", "was " + response.status() + ", expected " + APPROVED_STATUS);
        }
        if (!StringUtils.hasText(response.transactionId())) {
            return malformedResponse("transactionId", "was missing or blank");
        }
        if (response.balance() == null) {
            return malformedResponse("balance", "was missing");
        }
        if (response.balance().scale() > MAX_MONEY_SCALE) {
            return malformedResponse("balance",
                    "had scale " + response.balance().scale() + ", expected at most " + MAX_MONEY_SCALE);
        }
        return new ProviderResult.Approved(response.transactionId(), response.balance(), response.executedAt());
    }

    /**
     * A 4xx is only a business rejection when the body says so; 401/403/404/429 and gateway bodies
     * are answered by an intermediary, not by the provider contract, so they carry no business code.
     */
    private ProviderResult rejected(int status, ConvertibleClientHttpResponse response) throws IOException {
        RejectionResponse body;
        try {
            body = response.bodyTo(RejectionResponse.class);
        } catch (ResourceAccessException ex) {
            throw new ProviderTransientException("Provider rejection body could not be read", ex);
        } catch (RestClientException | HttpMessageConversionException ex) {
            log.warn("Provider answered 4xx with an unreadable body: status={}", status);
            return ProviderResult.unavailable(UNEXPECTED_4XX_RESPONSE);
        }
        if (body == null || !REJECTED_STATUS.equals(body.status()) || !StringUtils.hasText(body.code())) {
            log.warn("Provider answered 4xx without a business rejection: status={}", status);
            return ProviderResult.unavailable(UNEXPECTED_4XX_RESPONSE);
        }
        // The columns are bounded, and the provider is an external system: truncate here so an
        // oversized answer can never turn a rejection into a failed insert.
        return new ProviderResult.Rejected(
                truncate(body.code(), MAX_ERROR_CODE_LENGTH),
                truncate(body.message(), MAX_ERROR_MESSAGE_LENGTH));
    }

    private static ProviderResult malformedResponse(String field, String reason) {
        log.warn("Malformed provider response: field={}, reason={}", field, reason);
        return ProviderResult.unavailable(MALFORMED_RESPONSE);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record ExecuteRequest(String accountId, String type, BigDecimal amount, String currency) {
    }

    private record ExecuteResponse(String transactionId, String status, BigDecimal balance, Instant executedAt) {
    }

    private record RejectionResponse(String status, String code, String message) {
    }
}
