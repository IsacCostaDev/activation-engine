package com.activation.engine.application.dto;

import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;

import java.time.Instant;
import java.util.UUID;

public record ActivationResponseDto(
        UUID id,
        String externalId,
        String customerId,
        String productCode,
        RequestStatus status,
        String failureReason,
        int retryCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ActivationResponseDto fromDomain(ActivationRequest domain) {
        return new ActivationResponseDto(
                domain.getId(),
                domain.getExternalId(),
                domain.getCustomerId(),
                domain.getProductCode(),
                domain.getStatus(),
                domain.getFailureReason(),
                domain.getRetryCount(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
