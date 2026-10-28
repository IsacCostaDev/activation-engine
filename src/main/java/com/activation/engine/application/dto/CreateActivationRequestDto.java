package com.activation.engine.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateActivationRequestDto(
        @NotBlank(message = "externalId is required")
        @Size(max = 100, message = "externalId must not exceed 100 characters")
        String externalId,

        @NotBlank(message = "customerId is required")
        @Size(max = 100, message = "customerId must not exceed 100 characters")
        String customerId,

        @NotBlank(message = "productCode is required")
        @Size(max = 100, message = "productCode must not exceed 100 characters")
        String productCode
) {
}
