package com.activation.engine.infrastructure.web;

import com.activation.engine.domain.exception.DomainValidationException;
import com.activation.engine.domain.exception.IdempotencyConflictException;
import com.activation.engine.domain.exception.IllegalStateTransitionException;
import com.activation.engine.domain.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more request fields failed validation.");
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://api.activation-engine.io/problems/validation-error"));
        problem.setInstance(URI.create(request.getRequestURI()));

        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::mapFieldError)
                .toList();

        problem.setProperty("errors", fieldErrors);
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ProblemDetail> handleDomainValidation(DomainValidationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Domain Validation Error");
        problem.setType(URI.create("https://api.activation-engine.io/problems/domain-validation-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://api.activation-engine.io/problems/not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Idempotency Conflict");
        problem.setType(URI.create("https://api.activation-engine.io/problems/idempotency-conflict"));
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleStateTransition(IllegalStateTransitionException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Invalid State Transition");
        problem.setType(URI.create("https://api.activation-engine.io/problems/invalid-state-transition"));
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCircuitBreaker(CallNotPermittedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Partner activation service is currently unavailable. Circuit breaker is OPEN.");
        problem.setTitle("Service Unavailable");
        problem.setType(URI.create("https://api.activation-engine.io/problems/circuit-breaker-open"));
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Insufficient scope or permissions to access this resource.");
        problem.setTitle("Forbidden");
        problem.setType(URI.create("https://api.activation-engine.io/problems/forbidden"));
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource.");
        problem.setTitle("Unauthorized");
        problem.setType(URI.create("https://api.activation-engine.io/problems/unauthorized"));
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please contact support with the traceId.");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://api.activation-engine.io/problems/internal-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        enrichWithMetadata(problem);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private Map<String, String> mapFieldError(FieldError error) {
        return Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"
        );
    }

    private void enrichWithMetadata(ProblemDetail problem) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        problem.setProperty("timestamp", Instant.now().toString());
    }
}
