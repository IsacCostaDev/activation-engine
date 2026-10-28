package com.activation.engine.application.service;

import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.repository.ActivationRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ActivationRequestCreator {

    private final ActivationRequestRepository repository;

    public ActivationRequestCreator(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ActivationRequest insertInNewTransaction(ActivationRequest request) {
        return repository.save(request);
    }
}
