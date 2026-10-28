package com.activation.engine.application.service;

import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;
import com.activation.engine.domain.repository.ActivationRequestRepository;
import com.activation.engine.infrastructure.client.ExternalProviderClient;
import com.activation.engine.infrastructure.client.dto.ProviderActivationResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Optional;
import java.util.UUID;

@Component
public class ActivationProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(ActivationProcessingWorker.class);

    private final ActivationRequestRepository repository;
    private final ExternalProviderClient providerClient;
    private final int maxRetries;

    public ActivationProcessingWorker(
            ActivationRequestRepository repository,
            ExternalProviderClient providerClient,
            @Value("${app.reconciliation.max-retries:3}") int maxRetries) {
        this.repository = repository;
        this.providerClient = providerClient;
        this.maxRetries = maxRetries;
    }

    @Async
    @EventListener
    public void onActivationCreated(ActivationRequestService.ActivationCreatedEvent event) {
        processActivation(event.requestId());
    }

    public void processActivation(UUID requestId) {
        Optional<ActivationRequest> optionalRequest = repository.findById(requestId);
        if (optionalRequest.isEmpty()) {
            log.warn("Request not found for processing: {}", requestId);
            return;
        }

        ActivationRequest request = optionalRequest.get();
        if (request.getStatus() != RequestStatus.PENDING && request.getStatus() != RequestStatus.PROCESSING) {
            log.debug("Request {} is in terminal status {}, skipping", requestId, request.getStatus());
            return;
        }

        try {
            if (request.getStatus() == RequestStatus.PENDING) {
                request.startProcessing();
                request = repository.save(request);
            }

            ProviderActivationResponse response = providerClient.activateProduct(
                    request.getExternalId(),
                    request.getCustomerId(),
                    request.getProductCode()
            );

            request.complete();
            repository.save(request);
            log.info("Activation successfully completed for request: {}, activationCode: {}",
                    requestId, response != null ? response.activationCode() : "N/A");

        } catch (HttpClientErrorException ex) {
            log.error("Client error (4xx) received from provider for request {}: {}", requestId, ex.getStatusCode());
            request.fail("Provider rejected activation with status " + ex.getStatusCode());
            repository.save(request);

        } catch (HttpServerErrorException | ResourceAccessException ex) {
            handleTransientFailure(request, "Transient error: " + ex.getMessage());

        } catch (CallNotPermittedException ex) {
            log.warn("Circuit Breaker is OPEN for providerClient. Scheduling retry for request: {}", requestId);
            handleTransientFailure(request, "Circuit breaker OPEN: partner service temporarily unavailable");

        } catch (Exception ex) {
            log.error("Unexpected error activating request {}: {}", requestId, ex.getMessage(), ex);
            request.fail("Unexpected error: " + ex.getMessage());
            repository.save(request);
        }
    }

    private void handleTransientFailure(ActivationRequest request, String reason) {
        if (request.getRetryCount() < maxRetries) {
            log.info("Scheduling retry for request: {}, retry count: {}/{}",
                    request.getId(), request.getRetryCount() + 1, maxRetries);
            request.scheduleRetry(reason);
        } else {
            log.error("Max retries ({}) exceeded for request: {}. Marking as FAILED.", maxRetries, request.getId());
            request.fail("Max retries exceeded: " + reason);
        }
        repository.save(request);
    }
}
