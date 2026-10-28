package com.activation.engine.infrastructure.web;

import com.activation.engine.application.dto.CreateActivationRequestDto;
import com.activation.engine.application.service.ActivationRequestService;
import com.activation.engine.domain.exception.IdempotencyConflictException;
import com.activation.engine.domain.exception.ResourceNotFoundException;
import com.activation.engine.infrastructure.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActivationRequestController.class)
@Import({SecurityConfig.class, TraceIdFilter.class, GlobalExceptionHandler.class})
class ActivationRequestValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ActivationRequestService service;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Should return 400 Bad Request with ProblemDetail when required fields are missing")
    void shouldReturn400OnValidationFailure() throws Exception {
        CreateActivationRequestDto invalidPayload = new CreateActivationRequestDto("", " ", null);

        mockMvc.perform(post("/api/v1/requests")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("SCOPE_requests:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.type").value("https://api.activation-engine.io/problems/validation-error"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("Should return 404 Not Found with ProblemDetail when ID does not exist")
    void shouldReturn404OnResourceNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(id)).thenThrow(new ResourceNotFoundException("Not found: " + id));

        mockMvc.perform(get("/api/v1/requests/" + id)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("SCOPE_requests:read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("Should return 409 Conflict with ProblemDetail when externalId payload is divergent")
    void shouldReturn409OnIdempotencyConflict() throws Exception {
        CreateActivationRequestDto payload = new CreateActivationRequestDto("EXT-01", "CUST-01", "PROD-01");
        when(service.createRequest(any()))
                .thenThrow(new IdempotencyConflictException("Conflict with divergent parameters"));

        mockMvc.perform(post("/api/v1/requests")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("SCOPE_requests:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Idempotency Conflict"))
                .andExpect(jsonPath("$.traceId").exists());
    }
}
