package com.activation.engine.domain.repository;

import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivationRequestRepository {

    ActivationRequest save(ActivationRequest request);

    Optional<ActivationRequest> findById(UUID id);

    Optional<ActivationRequest> findByExternalId(String externalId);

    List<ActivationRequest> findEligibleForReconciliation(Instant updatedBefore, int maxRetries, int limit);

    List<ActivationRequest> findAllPaged(RequestStatus status, String customerId, int page, int size);

    long countAll(RequestStatus status, String customerId);
}
