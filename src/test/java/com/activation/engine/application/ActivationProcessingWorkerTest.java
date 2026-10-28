package com.activation.engine.application;

import com.activation.engine.application.service.ActivationProcessingWorker;
import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;
import com.activation.engine.domain.repository.ActivationRequestRepository;
import com.activation.engine.infrastructure.client.ExternalProviderClient;
import com.activation.engine.infrastructure.client.dto.ProviderActivationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivationProcessingWorkerTest {

    @Mock
    private ActivationRequestRepository repository;

    @Mock
    private ExternalProviderClient providerClient;

    private ActivationProcessingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ActivationProcessingWorker(repository, providerClient, 3);
        when(repository.save(any(ActivationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should successfully activate request when provider responds 200 OK")
    void shouldCompleteActivationOnProviderSuccess() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");
        UUID id = request.getId();
        when(repository.findById(id)).thenReturn(Optional.of(request));
        when(providerClient.activateProduct("EXT-001", "CUST-001", "INTERNET-500"))
                .thenReturn(new ProviderActivationResponse("SUCCESS", "ACT-999", "Activated"));

        worker.processActivation(id);

        ArgumentCaptor<ActivationRequest> captor = ArgumentCaptor.forClass(ActivationRequest.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        ActivationRequest saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(saved.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("Should permanently fail request when provider returns HTTP 400 Bad Request")
    void shouldFailPermanentlyOnClientError() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");
        UUID id = request.getId();
        when(repository.findById(id)).thenReturn(Optional.of(request));
        when(providerClient.activateProduct("EXT-001", "CUST-001", "INTERNET-500"))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid product"));

        worker.processActivation(id);

        ArgumentCaptor<ActivationRequest> captor = ArgumentCaptor.forClass(ActivationRequest.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        ActivationRequest saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(RequestStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("Provider rejected activation with status 400");
    }

    @Test
    @DisplayName("Should schedule retry and increment counter on transient 500 error when retries remain")
    void shouldScheduleRetryOnTransientServerError() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");
        UUID id = request.getId();
        when(repository.findById(id)).thenReturn(Optional.of(request));
        when(providerClient.activateProduct("EXT-001", "CUST-001", "INTERNET-500"))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server down"));

        worker.processActivation(id);

        ArgumentCaptor<ActivationRequest> captor = ArgumentCaptor.forClass(ActivationRequest.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        ActivationRequest saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getFailureReason()).contains("Transient error");
    }

    @Test
    @DisplayName("Should mark as FAILED when max retries limit is exceeded")
    void shouldMarkFailedWhenMaxRetriesExceeded() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");
        request.startProcessing();
        request.scheduleRetry("Attempt 1");
        request.startProcessing();
        request.scheduleRetry("Attempt 2");
        request.startProcessing();
        request.scheduleRetry("Attempt 3"); // retryCount = 3

        UUID id = request.getId();
        when(repository.findById(id)).thenReturn(Optional.of(request));
        when(providerClient.activateProduct("EXT-001", "CUST-001", "INTERNET-500"))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Gateway timeout"));

        worker.processActivation(id);

        ArgumentCaptor<ActivationRequest> captor = ArgumentCaptor.forClass(ActivationRequest.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        ActivationRequest saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(RequestStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("Max retries exceeded");
    }
}
