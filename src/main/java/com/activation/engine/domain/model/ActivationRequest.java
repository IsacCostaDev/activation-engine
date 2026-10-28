package com.activation.engine.domain.model;

import com.activation.engine.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ActivationRequest {

    private final UUID id;
    private final String externalId;
    private final String customerId;
    private final String productCode;
    private RequestStatus status;
    private String failureReason;
    private int retryCount;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public ActivationRequest(UUID id, String externalId, String customerId, String productCode,
                             RequestStatus status, String failureReason, int retryCount,
                             long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.externalId = validateNotBlank(externalId, "externalId cannot be blank");
        this.customerId = validateNotBlank(customerId, "customerId cannot be blank");
        this.productCode = validateNotBlank(productCode, "productCode cannot be blank");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public static ActivationRequest create(String externalId, String customerId, String productCode) {
        Instant now = Instant.now();
        return new ActivationRequest(
                UUID.randomUUID(),
                externalId,
                customerId,
                productCode,
                RequestStatus.PENDING,
                null,
                0,
                0L,
                now,
                now
        );
    }

    public void startProcessing() {
        this.status.validateTransitionTo(RequestStatus.PROCESSING);
        this.status = RequestStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status.validateTransitionTo(RequestStatus.COMPLETED);
        this.status = RequestStatus.COMPLETED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status.validateTransitionTo(RequestStatus.FAILED);
        this.status = RequestStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void scheduleRetry(String reason) {
        this.status.validateTransitionTo(RequestStatus.PENDING);
        this.status = RequestStatus.PENDING;
        this.failureReason = reason;
        this.retryCount++;
        this.updatedAt = Instant.now();
    }

    public boolean isIdempotentWith(String customerId, String productCode) {
        return Objects.equals(this.customerId, customerId)
                && Objects.equals(this.productCode, productCode);
    }

    private static String validateNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainValidationException(message);
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
