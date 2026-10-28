package com.activation.engine.infrastructure.client;

import com.activation.engine.application.service.ActivationProcessingWorker;
import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.repository.ActivationRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ActivationReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActivationReconciliationScheduler.class);

    private final ActivationRequestRepository repository;
    private final ActivationProcessingWorker worker;
    private final int thresholdSeconds;
    private final int maxRetries;

    public ActivationReconciliationScheduler(
            ActivationRequestRepository repository,
            ActivationProcessingWorker worker,
            @Value("${app.reconciliation.threshold-seconds:30}") int thresholdSeconds,
            @Value("${app.reconciliation.max-retries:3}") int maxRetries) {
        this.repository = repository;
        this.worker = worker;
        this.thresholdSeconds = thresholdSeconds;
        this.maxRetries = maxRetries;
    }

    @Scheduled(cron = "${app.reconciliation.cron:0/30 * * * * *}")
    public void reconcilePendingRequests() {
        Instant threshold = Instant.now().minus(thresholdSeconds, ChronoUnit.SECONDS);
        List<ActivationRequest> stuckRequests = repository.findEligibleForReconciliation(threshold, maxRetries, 50);

        if (!stuckRequests.isEmpty()) {
            log.info("Reconciliation scheduler found {} requests requiring reprocessing", stuckRequests.size());
            for (ActivationRequest request : stuckRequests) {
                try {
                    worker.processActivation(request.getId());
                } catch (Exception ex) {
                    log.error("Failed to reconcile request {}: {}", request.getId(), ex.getMessage(), ex);
                }
            }
        }
    }
}
