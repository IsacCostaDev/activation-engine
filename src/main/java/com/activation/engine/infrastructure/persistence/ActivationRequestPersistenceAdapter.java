package com.activation.engine.infrastructure.persistence;

import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;
import com.activation.engine.domain.repository.ActivationRequestRepository;
import com.activation.engine.infrastructure.persistence.entity.ActivationRequestEntity;
import com.activation.engine.infrastructure.persistence.repository.SpringDataActivationRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ActivationRequestPersistenceAdapter implements ActivationRequestRepository {

    private final SpringDataActivationRequestRepository springDataRepository;

    public ActivationRequestPersistenceAdapter(SpringDataActivationRequestRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    @Transactional
    public ActivationRequest save(ActivationRequest request) {
        Optional<ActivationRequestEntity> existing = springDataRepository.findById(request.getId());
        ActivationRequestEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.updateFromDomain(request);
        } else {
            entity = ActivationRequestEntity.fromDomain(request);
        }
        return springDataRepository.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivationRequest> findById(UUID id) {
        return springDataRepository.findById(id).map(ActivationRequestEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ActivationRequest> findByExternalId(String externalId) {
        return springDataRepository.findByExternalId(externalId).map(ActivationRequestEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivationRequest> findEligibleForReconciliation(Instant updatedBefore, int maxRetries, int limit) {
        return springDataRepository.findEligibleForReconciliation(updatedBefore, maxRetries, PageRequest.of(0, limit))
                .stream()
                .map(ActivationRequestEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivationRequest> findAllPaged(RequestStatus status, String customerId, int page, int size) {
        Page<ActivationRequestEntity> result = springDataRepository.findAllFiltered(
                status, customerId, PageRequest.of(page, size));
        return result.stream().map(ActivationRequestEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll(RequestStatus status, String customerId) {
        return springDataRepository.countFiltered(status, customerId);
    }
}
