package com.activation.engine.infrastructure.client.dto;

public record ProviderActivationResponse(
        String status,
        String activationCode,
        String message
) {
}
