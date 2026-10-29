package com.example.challenge.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Wires the provider HTTP client and the resilience4j instances used around it.
 * <p>
 * The instances are created from {@link RetryRegistry} and {@link CircuitBreakerRegistry}, which
 * are populated from {@code resilience4j.*} in {@code application.yml}; nothing is hardcoded here
 * except the instance name shared by the adapter and the configuration file.
 * <p>
 * Each instance logs its own events so a retry or a state transition is visible in production
 * without turning on DEBUG for the whole resilience4j package. The listeners are registered when the
 * bean is created, so they run exactly once per event; the adapter must not subscribe again.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderConfig {

    public static final String PROVIDER_INSTANCE = "provider";

    @Bean
    RestClient providerRestClient(RestClient.Builder builder, ProviderProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                // HTTP/1.1: the JDK client defaults to HTTP/2, which for plain http starts an h2c
                // upgrade that intermediaries (and some provider gateways) handle poorly.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    Retry providerRetry(RetryRegistry registry) {
        Retry retry = registry.retry(PROVIDER_INSTANCE);
        retry.getEventPublisher().onRetry(event ->
                log.warn("Provider call failed, retrying: attempt {}", event.getNumberOfRetryAttempts()));
        return retry;
    }

    @Bean
    CircuitBreaker providerCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker circuitBreaker = registry.circuitBreaker(PROVIDER_INSTANCE);
        circuitBreaker.getEventPublisher().onStateTransition(event ->
                logStateTransition(event.getStateTransition()));
        return circuitBreaker;
    }

    private static void logStateTransition(CircuitBreaker.StateTransition transition) {
        CircuitBreaker.State to = transition.getToState();
        if (to == CircuitBreaker.State.OPEN) {
            log.warn("Provider circuit breaker: {} -> {}", transition.getFromState(), to);
        } else {
            log.info("Provider circuit breaker: {} -> {}", transition.getFromState(), to);
        }
    }
}
