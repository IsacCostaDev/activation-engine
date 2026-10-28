package com.activation.engine.domain.model;

import com.activation.engine.domain.exception.IllegalStateTransitionException;

public enum RequestStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(RequestStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == PROCESSING || target == FAILED;
            case PROCESSING -> target == COMPLETED || target == FAILED || target == PENDING;
            case COMPLETED, FAILED -> false;
        };
    }

    public void validateTransitionTo(RequestStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateTransitionException(
                    "Invalid state transition from " + this + " to " + target);
        }
    }
}
