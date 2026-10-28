package com.activation.engine.infrastructure.persistence.entity;

import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activation_requests")
public class ActivationRequestEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true, length = 100)
    private String externalId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RequestStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ActivationRequestEntity() {
    }

    public static ActivationRequestEntity fromDomain(ActivationRequest domain) {
        ActivationRequestEntity entity = new ActivationRequestEntity();
        entity.id = domain.getId();
        entity.externalId = domain.getExternalId();
        entity.customerId = domain.getCustomerId();
        entity.productCode = domain.getProductCode();
        entity.status = domain.getStatus();
        entity.failureReason = domain.getFailureReason();
        entity.retryCount = domain.getRetryCount();
        entity.version = domain.getVersion();
        entity.createdAt = domain.getCreatedAt();
        entity.updatedAt = domain.getUpdatedAt();
        return entity;
    }

    public ActivationRequest toDomain() {
        return new ActivationRequest(
                id,
                externalId,
                customerId,
                productCode,
                status,
                failureReason,
                retryCount,
                version,
                createdAt,
                updatedAt
        );
    }

    public void updateFromDomain(ActivationRequest domain) {
        this.status = domain.getStatus();
        this.failureReason = domain.getFailureReason();
        this.retryCount = domain.getRetryCount();
        this.updatedAt = domain.getUpdatedAt();
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
