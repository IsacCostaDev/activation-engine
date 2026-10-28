package com.activation.engine.domain;

import com.activation.engine.domain.exception.DomainValidationException;
import com.activation.engine.domain.exception.IllegalStateTransitionException;
import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivationRequestTest {

    @Test
    @DisplayName("Should create activation request in PENDING status with valid fields")
    void shouldCreateActivationRequestSuccessfully() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");

        assertThat(request.getId()).isNotNull();
        assertThat(request.getExternalId()).isEqualTo("EXT-001");
        assertThat(request.getCustomerId()).isEqualTo("CUST-001");
        assertThat(request.getProductCode()).isEqualTo("INTERNET-500");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(request.getRetryCount()).isZero();
        assertThat(request.getFailureReason()).isNull();
        assertThat(request.getCreatedAt()).isNotNull();
        assertThat(request.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject blank required attributes")
    void shouldRejectBlankAttributes() {
        assertThatThrownBy(() -> ActivationRequest.create(" ", "CUST-001", "INTERNET-500"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("externalId");

        assertThatThrownBy(() -> ActivationRequest.create("EXT-001", "", "INTERNET-500"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("customerId");

        assertThatThrownBy(() -> ActivationRequest.create("EXT-001", "CUST-001", null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("productCode");
    }

    @Test
    @DisplayName("Should execute valid state transitions: PENDING -> PROCESSING -> COMPLETED")
    void shouldTransitionToCompleted() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");

        request.startProcessing();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.PROCESSING);

        request.complete();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("Should execute valid state transitions: PENDING -> PROCESSING -> FAILED")
    void shouldTransitionToFailed() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");

        request.startProcessing();
        request.fail("Provider returned 400 Bad Request");

        assertThat(request.getStatus()).isEqualTo(RequestStatus.FAILED);
        assertThat(request.getFailureReason()).isEqualTo("Provider returned 400 Bad Request");
    }

    @Test
    @DisplayName("Should reject invalid state transition: COMPLETED -> PROCESSING")
    void shouldRejectTransitionFromCompleted() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");
        request.startProcessing();
        request.complete();

        assertThatThrownBy(request::startProcessing)
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("from COMPLETED to PROCESSING");
    }

    @Test
    @DisplayName("Should reject invalid state transition: FAILED -> COMPLETED")
    void shouldRejectTransitionFromFailed() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");
        request.startProcessing();
        request.fail("Timeout");

        assertThatThrownBy(request::complete)
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("from FAILED to COMPLETED");
    }

    @Test
    @DisplayName("Should schedule retry incrementing counter and returning to PENDING")
    void shouldScheduleRetryCorrectly() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");
        request.startProcessing();
        request.scheduleRetry("Network connection reset");

        assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
        assertThat(request.getRetryCount()).isEqualTo(1);
        assertThat(request.getFailureReason()).isEqualTo("Network connection reset");
    }

    @Test
    @DisplayName("Should correctly evaluate payload idempotency match")
    void shouldEvaluateIdempotency() {
        ActivationRequest request = ActivationRequest.create("EXT-001", "CUST-001", "INTERNET-500");

        assertThat(request.isIdempotentWith("CUST-001", "INTERNET-500")).isTrue();
        assertThat(request.isIdempotentWith("CUST-002", "INTERNET-500")).isFalse();
        assertThat(request.isIdempotentWith("CUST-001", "INTERNET-1000")).isFalse();
    }
}
