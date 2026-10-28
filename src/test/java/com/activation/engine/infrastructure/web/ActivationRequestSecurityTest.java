package com.activation.engine.infrastructure.web;

import com.activation.engine.application.dto.CreateActivationRequestDto;
import com.activation.engine.application.dto.CreationResult;
import com.activation.engine.application.service.ActivationRequestService;
import com.activation.engine.domain.model.ActivationRequest;
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
class ActivationRequestSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ActivationRequestService service;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Should return 401 Unauthorized on POST when request lacks Bearer token")
    void shouldReturn401WhenPostWithoutToken() throws Exception {
        CreateActivationRequestDto payload = new CreateActivationRequestDto("EXT-01", "CUST-01", "PROD-01");

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("Should return 403 Forbidden on POST when token has only read scope")
    void shouldReturn403WhenPostWithOnlyReadScope() throws Exception {
        CreateActivationRequestDto payload = new CreateActivationRequestDto("EXT-01", "CUST-01", "PROD-01");

        mockMvc.perform(post("/api/v1/requests")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("SCOPE_requests:read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("Should return 201 Created on POST when token has write scope")
    void shouldReturn201WhenPostWithWriteScope() throws Exception {
        CreateActivationRequestDto payload = new CreateActivationRequestDto("EXT-01", "CUST-01", "PROD-01");
        ActivationRequest created = ActivationRequest.create("EXT-01", "CUST-01", "PROD-01");
        when(service.createRequest(any())).thenReturn(new CreationResult(created, false));

        mockMvc.perform(post("/api/v1/requests")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("SCOPE_requests:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.externalId").value("EXT-01"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized on GET when request lacks Bearer token")
    void shouldReturn401WhenGetWithoutToken() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/requests/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Should return 200 OK on GET when token has read scope")
    void shouldReturn200WhenGetWithReadScope() throws Exception {
        UUID id = UUID.randomUUID();
        ActivationRequest request = ActivationRequest.create("EXT-01", "CUST-01", "PROD-01");
        when(service.getById(id)).thenReturn(request);

        mockMvc.perform(get("/api/v1/requests/" + id)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("SCOPE_requests:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").value("EXT-01"))
                .andExpect(jsonPath("$.customerId").value("CUST-01"));
    }
}
