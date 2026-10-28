package com.activation.engine.application.service;

import com.activation.engine.application.dto.ActivationResponseDto;
import com.activation.engine.application.dto.CreateActivationRequestDto;
import com.activation.engine.application.dto.CreationResult;
import com.activation.engine.application.dto.PagedResponseDto;
import com.activation.engine.domain.exception.IdempotencyConflictException;
import com.activation.engine.domain.exception.ResourceNotFoundException;
import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;
import com.activation.engine.domain.repository.ActivationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ActivationRequestService {

    private static final Logger log = LoggerFactory.getLogger(ActivationRequestService.class);

    private final ActivationRequestRepository repository;
    private final ActivationRequestCreator creator;
    private final ApplicationEventPublisher eventPublisher;

    public ActivationRequestService(ActivationRequestRepository repository,
                                    ActivationRequestCreator creator,
                                    ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.creator = creator;
        this.eventPublisher = eventPublisher;
    }

    public CreationResult createRequest(CreateActivationRequestDto dto) {
        Optional<ActivationRequest> existing = repository.findByExternalId(dto.externalId());
        if (existing.isPresent()) {
            ActivationRequest req = existing.get();
            if (req.isIdempotentWith(dto.customerId(), dto.productCode())) {
                log.info("Idempotent retry detected for externalId: {}", dto.externalId());
                return new CreationResult(req, true);
            }
            throw new IdempotencyConflictException(
                    "Request with externalId '" + dto.externalId() +
                    "' already exists with divergent customerId or productCode.");
        }

        ActivationRequest newRequest = ActivationRequest.create(
                dto.externalId(), dto.customerId(), dto.productCode());

        try {
            ActivationRequest saved = creator.insertInNewTransaction(newRequest);
            log.info("Created new activation request with id: {}, externalId: {}", saved.getId(), saved.getExternalId());
            eventPublisher.publishEvent(new ActivationCreatedEvent(saved.getId()));
            return new CreationResult(saved, false);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent insert race condition for externalId: {}. Recovering in clean transaction...", dto.externalId());
            ActivationRequest concurrentWinner = repository.findByExternalId(dto.externalId())
                    .orElseThrow(() -> ex);

            if (concurrentWinner.isIdempotentWith(dto.customerId(), dto.productCode())) {
                return new CreationResult(concurrentWinner, true);
            }
            throw new IdempotencyConflictException(
                    "Concurrent conflict: externalId '" + dto.externalId() +
                    "' was registered concurrently with divergent parameters.");
        }
    }

    @Transactional(readOnly = true)
    public ActivationRequest getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activation request not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public PagedResponseDto<ActivationResponseDto> listRequests(RequestStatus status, String customerId, int page, int size) {
        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = (size <= 0 || size > 100) ? 20 : size;

        List<ActivationRequest> requests = repository.findAllPaged(status, customerId, sanitizedPage, sanitizedSize);
        long total = repository.countAll(status, customerId);

        List<ActivationResponseDto> dtos = requests.stream()
                .map(ActivationResponseDto::fromDomain)
                .toList();

        return PagedResponseDto.of(dtos, sanitizedPage, sanitizedSize, total);
    }

    public record ActivationCreatedEvent(UUID requestId) {
    }
}
