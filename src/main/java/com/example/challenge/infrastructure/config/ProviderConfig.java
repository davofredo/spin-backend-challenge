package com.example.challenge.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
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
 */
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
        return registry.retry(PROVIDER_INSTANCE);
    }

    @Bean
    CircuitBreaker providerCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(PROVIDER_INSTANCE);
    }
}
