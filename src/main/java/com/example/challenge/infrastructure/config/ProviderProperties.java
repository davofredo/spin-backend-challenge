package com.example.challenge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Connection settings of the external provider, bound from the {@code provider} prefix.
 * <p>
 * Timeouts are explicit on purpose: the default (infinite) read timeout would keep a request
 * hanging until the servlet container gives up, and the outcome of that transaction would never be
 * recorded.
 */
@ConfigurationProperties(prefix = "provider")
public record ProviderProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {

    public ProviderProperties {
        Objects.requireNonNull(baseUrl, "provider.base-url must be configured");
        Objects.requireNonNull(connectTimeout, "provider.connect-timeout must be configured");
        Objects.requireNonNull(readTimeout, "provider.read-timeout must be configured");
    }
}
