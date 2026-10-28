package com.example.challenge.infrastructure.adapter.out.provider;

import com.example.challenge.application.port.out.ProviderResult;
import com.example.challenge.application.port.out.TransactionProvider;
import com.example.challenge.domain.model.TransactionRequest;
import com.example.challenge.domain.model.TransactionType;
import com.example.challenge.infrastructure.config.ProviderConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the adapter against the real resilience4j configuration from {@code application.yml},
 * with the timeouts and the circuit-breaker window shortened so the tests stay fast.
 */
@SpringBootTest(
        classes = ProviderHttpAdapterTest.ProviderOnlyApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "provider.connect-timeout=200ms",
                "provider.read-timeout=500ms",
                "resilience4j.retry.instances.provider.wait-duration=10ms",
                "resilience4j.retry.instances.provider.enable-exponential-backoff=false",
                "resilience4j.circuitbreaker.instances.provider.sliding-window-size=4",
                "resilience4j.circuitbreaker.instances.provider.minimum-number-of-calls=4",
                "resilience4j.circuitbreaker.instances.provider.wait-duration-in-open-state=60s"
        })
class ProviderHttpAdapterTest {

    private static final String EXECUTE_PATH = "/provider/v1/execute";
    private static final String APPROVED_BODY = """
            {"transactionId":"provider-txn-1","status":"APPROVED","balance":900.00,
             "executedAt":"2026-01-01T12:00:00Z"}
            """;

    private static final WireMockServer PROVIDER = new WireMockServer(options().dynamicPort());

    static {
        PROVIDER.start();
    }

    @DynamicPropertySource
    static void providerBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("provider.base-url", PROVIDER::baseUrl);
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop();
    }

    @Autowired
    private TransactionProvider provider;

    @Autowired
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void resetProviderAndCircuitBreaker() {
        PROVIDER.resetAll();
        circuitBreaker.reset();
    }

    @Test
    void shouldReturnApproved_whenProviderAnswers200() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(okJson(APPROVED_BODY)));

        ProviderResult result = provider.execute(request());

        assertThat(result).isInstanceOfSatisfying(ProviderResult.Approved.class, approved -> {
            assertThat(approved.providerTransactionId()).isEqualTo("provider-txn-1");
            assertThat(approved.balance()).isEqualByComparingTo("900.00");
            assertThat(approved.executedAt()).isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
        });
        PROVIDER.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
    }

    @Test
    void shouldSendAccountTypeAmountAndCurrency_whenCallingProvider() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(okJson(APPROVED_BODY)));

        provider.execute(request());

        PROVIDER.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH))
                .withRequestBody(matchingJsonPath("$.accountId", equalTo("acc-1")))
                .withRequestBody(matchingJsonPath("$.type", equalTo("DEBIT")))
                .withRequestBody(containing("\"amount\":100.00"))
                .withRequestBody(matchingJsonPath("$.currency", equalTo("MXN"))));
    }

    @Test
    void shouldReturnRejectedWithProviderCode_whenProviderAnswers4xx() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(rejection(422, "INSUFFICIENT_FUNDS", "Not enough funds")));

        ProviderResult result = provider.execute(request());

        assertThat(result).isEqualTo(new ProviderResult.Rejected("INSUFFICIENT_FUNDS", "Not enough funds"));
    }

    @Test
    void shouldNotRetry_whenProviderRejects() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(rejection(422, "INSUFFICIENT_FUNDS", "Not enough funds")));

        provider.execute(request());

        PROVIDER.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void shouldReturnUnavailable_whenProviderAnswers5xx() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(aResponse().withStatus(503)));

        assertThat(provider.execute(request())).isInstanceOf(ProviderResult.Unavailable.class);
        PROVIDER.verify(3, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
    }

    @Test
    void shouldReturnUnavailable_whenProviderTimesOut() {
        PROVIDER.stubFor(post(EXECUTE_PATH)
                .willReturn(aResponse().withStatus(200).withFixedDelay(1_500)));

        assertThat(provider.execute(request())).isInstanceOf(ProviderResult.Unavailable.class);
    }

    @Test
    void shouldReturnUnavailable_whenConnectionIsReset() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(provider.execute(request())).isInstanceOf(ProviderResult.Unavailable.class);
        PROVIDER.verify(3, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
    }

    @Test
    void shouldReturnUnavailable_whenBodyIsMalformed() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(okJson("{not-json")));

        assertThat(provider.execute(request())).isInstanceOf(ProviderResult.Unavailable.class);
        PROVIDER.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
    }

    @Test
    void shouldReturnUnavailable_whenRejectionHasNoCode() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(aResponse()
                .withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"REJECTED\",\"message\":\"No code\"}")));

        assertThat(provider.execute(request())).isInstanceOf(ProviderResult.Unavailable.class);
    }

    @Test
    void shouldRetryAndReturnApproved_whenFirstCallFailsWith5xx() {
        PROVIDER.stubFor(post(EXECUTE_PATH)
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
        PROVIDER.stubFor(post(EXECUTE_PATH)
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson(APPROVED_BODY)));

        assertThat(provider.execute(request())).isInstanceOf(ProviderResult.Approved.class);
        PROVIDER.verify(2, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
    }

    @Test
    void shouldOpenCircuit_whenFailuresReachThreshold() {
        PROVIDER.stubFor(post(EXECUTE_PATH).willReturn(aResponse().withStatus(503)));

        provider.execute(request());
        provider.execute(request());

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        PROVIDER.verify(4, postRequestedFor(urlEqualTo(EXECUTE_PATH)));

        assertThat(provider.execute(request())).isInstanceOf(ProviderResult.Unavailable.class);
        PROVIDER.verify(4, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
    }

    @Test
    void shouldTruncateRejectionFields_whenProviderReturnsOversizedValues() {
        PROVIDER.stubFor(post(EXECUTE_PATH)
                .willReturn(rejection(422, "C".repeat(100), "M".repeat(2_000))));

        ProviderResult result = provider.execute(request());

        assertThat(result).isInstanceOfSatisfying(ProviderResult.Rejected.class, rejected -> {
            assertThat(rejected.code())
                    .hasSize(ProviderHttpAdapter.MAX_ERROR_CODE_LENGTH)
                    .isEqualTo("C".repeat(ProviderHttpAdapter.MAX_ERROR_CODE_LENGTH));
            assertThat(rejected.message())
                    .hasSize(ProviderHttpAdapter.MAX_ERROR_MESSAGE_LENGTH)
                    .isEqualTo("M".repeat(ProviderHttpAdapter.MAX_ERROR_MESSAGE_LENGTH));
        });
        PROVIDER.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
    }

    private static ResponseDefinitionBuilder rejection(int status, String code, String message) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"status":"REJECTED","code":"%s","message":"%s"}
                        """.formatted(code, message));
    }

    private static TransactionRequest request() {
        return new TransactionRequest(
                UUID.fromString("8f3b0b0e-6a0f-4f9a-9c1e-2b0b0e6a0f4f"),
                "acc-1",
                TransactionType.DEBIT,
                new BigDecimal("100.00"),
                "MXN",
                "test transaction",
                null,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({ProviderConfig.class, ProviderHttpAdapter.class})
    static class ProviderOnlyApplication {
    }
}
