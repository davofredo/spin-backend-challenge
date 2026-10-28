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
 * throw {@link ProviderTransientException}. Malformed or unexpected responses are reported as
 * {@link ProviderResult.Unavailable} without retrying, because repeating a call that the provider
 * did answer cannot fix a contract violation.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProviderHttpAdapter implements TransactionProvider {

    /** Matches {@code transactions.error_code VARCHAR(64)}. */
    static final int MAX_ERROR_CODE_LENGTH = 64;

    /** Matches {@code transactions.error_message VARCHAR(500)}. */
    static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private static final String EXECUTE_PATH = "/provider/v1/execute";
    private static final String MALFORMED_RESPONSE = "Malformed provider response";
    private static final String UNEXPECTED_STATUS = "Unexpected provider status";

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
            return rejected(response.bodyTo(RejectionResponse.class));
        }
        if (status.is5xxServerError()) {
            throw new ProviderTransientException("Provider responded with status " + status.value());
        }
        return ProviderResult.unavailable(UNEXPECTED_STATUS + " " + status.value());
    }

    private ProviderResult approved(ExecuteResponse response) {
        if (response == null
                || !StringUtils.hasText(response.transactionId())
                || response.balance() == null) {
            return ProviderResult.unavailable(MALFORMED_RESPONSE);
        }
        return new ProviderResult.Approved(response.transactionId(), response.balance(), response.executedAt());
    }

    private ProviderResult rejected(RejectionResponse response) {
        if (response == null || !StringUtils.hasText(response.code())) {
            return ProviderResult.unavailable(MALFORMED_RESPONSE);
        }
        // The columns are bounded, and the provider is an external system: truncate here so an
        // oversized answer can never turn a rejection into a failed insert.
        return new ProviderResult.Rejected(
                truncate(response.code(), MAX_ERROR_CODE_LENGTH),
                truncate(response.message(), MAX_ERROR_MESSAGE_LENGTH));
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
