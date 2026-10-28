package com.activation.engine.infrastructure.web;

import com.activation.engine.application.dto.ActivationResponseDto;
import com.activation.engine.application.dto.CreateActivationRequestDto;
import com.activation.engine.application.dto.CreationResult;
import com.activation.engine.application.dto.PagedResponseDto;
import com.activation.engine.application.service.ActivationRequestService;
import com.activation.engine.domain.model.ActivationRequest;
import com.activation.engine.domain.model.RequestStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class ActivationRequestController {

    private final ActivationRequestService service;

    public ActivationRequestController(ActivationRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ActivationResponseDto> createRequest(
            @Valid @RequestBody CreateActivationRequestDto requestDto) {

        CreationResult result = service.createRequest(requestDto);
        ActivationResponseDto responseBody = ActivationResponseDto.fromDomain(result.request());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.request().getId())
                .toUri();

        if (result.isRetry()) {
            return ResponseEntity.ok()
                    .location(location)
                    .body(responseBody);
        }

        return ResponseEntity.created(location)
                .body(responseBody);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivationResponseDto> getById(@PathVariable UUID id) {
        ActivationRequest request = service.getById(id);
        return ResponseEntity.ok(ActivationResponseDto.fromDomain(request));
    }

    @GetMapping
    public ResponseEntity<PagedResponseDto<ActivationResponseDto>> listRequests(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PagedResponseDto<ActivationResponseDto> response = service.listRequests(status, customerId, page, size);
        return ResponseEntity.ok(response);
    }
}
