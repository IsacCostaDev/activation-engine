package com.activation.engine.application.dto;

import com.activation.engine.domain.model.ActivationRequest;

public record CreationResult(
        ActivationRequest request,
        boolean isRetry
) {
}
