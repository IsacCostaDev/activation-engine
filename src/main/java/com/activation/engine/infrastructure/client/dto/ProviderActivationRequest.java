package com.activation.engine.infrastructure.client.dto;

public record ProviderActivationRequest(
        String customerId,
        String productCode
) {
}
