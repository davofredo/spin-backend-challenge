package com.example.challenge.application.port.out;

import com.example.challenge.domain.model.TransactionRequest;

/**
 * Executes a transaction against the external provider.
 * <p>
 * Implementations translate transport failures into a {@link ProviderResult}; the application layer
 * never sees HTTP concepts. Implementations must not throw for an unreachable provider: an unknown
 * outcome is reported as {@link ProviderResult.Unavailable}.
 */
public interface TransactionProvider {

    ProviderResult execute(TransactionRequest request);
}
