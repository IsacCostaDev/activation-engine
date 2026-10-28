package com.activation.engine.application;

import com.activation.engine.application.dto.CreateActivationRequestDto;
import com.activation.engine.application.dto.CreationResult;
import com.activation.engine.application.service.ActivationRequestCreator;
import com.activation.engine.application.service.ActivationRequestService;
import com.activation.engine.domain.exception.IdempotencyConflictException;
import com.activation.engine.domain.exception.ResourceNotFoundException;
import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;
import com.activation.engine.domain.repository.ActivationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivationRequestServiceTest {

    @Mock
    private ActivationRequestRepository repository;

    @Mock
    private ActivationRequestCreator creator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ActivationRequestService service;

    @BeforeEach
    void setUp() {
        service = new ActivationRequestService(repository, creator, eventPublisher);
    }

    @Test
    @DisplayName("Should create new activation request when externalId does not exist")
    void shouldCreateNewRequest() {
        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-100", "CUST-100", "INTERNET-500");
        when(repository.findByExternalId("EXT-100")).thenReturn(Optional.empty());
        when(creator.insertInNewTransaction(any(ActivationRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreationResult result = service.createRequest(dto);

        assertThat(result.isRetry()).isFalse();
        assertThat(result.request().getExternalId()).isEqualTo("EXT-100");
        assertThat(result.request().getStatus()).isEqualTo(RequestStatus.PENDING);

        ArgumentCaptor<ActivationRequestService.ActivationCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(ActivationRequestService.ActivationCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().requestId()).isEqualTo(result.request().getId());
    }

    @Test
    @DisplayName("Should detect idempotent retry and return existing request with isRetry=true")
    void shouldReturnExistingOnIdempotentRetry() {
        ActivationRequest existing = ActivationRequest.create("EXT-100", "CUST-100", "INTERNET-500");
        when(repository.findByExternalId("EXT-100")).thenReturn(Optional.of(existing));

        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-100", "CUST-100", "INTERNET-500");
        CreationResult result = service.createRequest(dto);

        assertThat(result.isRetry()).isTrue();
        assertThat(result.request()).isSameAs(existing);
        verify(creator, never()).insertInNewTransaction(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Should throw IdempotencyConflictException when externalId exists with divergent payload")
    void shouldThrowConflictOnDivergentPayload() {
        ActivationRequest existing = ActivationRequest.create("EXT-100", "CUST-100", "INTERNET-500");
        when(repository.findByExternalId("EXT-100")).thenReturn(Optional.of(existing));

        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-100", "CUST-100", "FIBRA-1000");

        assertThatThrownBy(() -> service.createRequest(dto))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("already exists with divergent");
    }

    @Test
    @DisplayName("Should recover transparently from concurrency DataIntegrityViolationException when payload is identical")
    void shouldRecoverFromConcurrentDataIntegrityViolation() {
        when(repository.findByExternalId("EXT-100"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ActivationRequest.create("EXT-100", "CUST-100", "INTERNET-500")));

        when(creator.insertInNewTransaction(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        CreateActivationRequestDto dto = new CreateActivationRequestDto("EXT-100", "CUST-100", "INTERNET-500");
        CreationResult result = service.createRequest(dto);

        assertThat(result.isRetry()).isTrue();
        assertThat(result.request().getExternalId()).isEqualTo("EXT-100");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when querying non-existing ID")
    void shouldThrowNotFoundWhenQueryingNonExistingId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
