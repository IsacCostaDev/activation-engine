package com.activation.engine.infrastructure;

import com.activation.engine.application.dto.CreateActivationRequestDto;
import com.activation.engine.application.dto.CreationResult;
import com.activation.engine.application.service.ActivationRequestService;
import com.activation.engine.infrastructure.persistence.repository.SpringDataActivationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private ActivationRequestService service;

    @Autowired
    private SpringDataActivationRequestRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Should prevent duplicates under concurrent race condition with 10 simultaneous threads")
    void shouldPreventDuplicatesUnderConcurrency() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        CreateActivationRequestDto payload = new CreateActivationRequestDto(
                "RACE-COND-001", "CUST-999", "INTERNET-500");

        List<CreationResult> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    CreationResult result = service.createRequest(payload);
                    results.add(result);
                } catch (Exception ex) {
                    errorCount.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errorCount.get()).isZero();
        assertThat(results).hasSize(threadCount);

        long newlyCreated = results.stream().filter(r -> !r.isRetry()).count();
        long recoveredRetries = results.stream().filter(CreationResult::isRetry).count();

        assertThat(newlyCreated).isEqualTo(1);
        assertThat(recoveredRetries).isEqualTo(threadCount - 1);

        long databaseCount = repository.count();
        assertThat(databaseCount).isEqualTo(1);
    }
}
