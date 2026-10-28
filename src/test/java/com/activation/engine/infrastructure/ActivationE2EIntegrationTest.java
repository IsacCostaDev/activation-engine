package com.activation.engine.infrastructure;

import com.activation.engine.application.dto.CreateActivationRequestDto;
import com.activation.engine.application.dto.CreationResult;
import com.activation.engine.application.service.ActivationRequestService;
import com.activation.engine.domain.model.RequestStatus;
import com.activation.engine.infrastructure.persistence.repository.SpringDataActivationRequestRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ActivationE2EIntegrationTest extends AbstractIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private ActivationRequestService service;

    @Autowired
    private SpringDataActivationRequestRepository repository;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureWireMockPort(DynamicPropertyRegistry registry) {
        registry.add("app.provider.base-url", () -> wireMockServer.baseUrl());
    }

    @BeforeEach
    void resetState() {
        repository.deleteAll();
        wireMockServer.resetAll();
    }

    @Test
    @DisplayName("Should execute end-to-end asynchronous activation: PENDING -> PROCESSING -> COMPLETED with WireMock")
    void shouldCompleteEndToEndActivation() {
        wireMockServer.stubFor(post(urlEqualTo("/provider/v1/activate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"SUCCESS\",\"activationCode\":\"ACT-2026-OK\",\"message\":\"Provisioned\"}")));

        CreateActivationRequestDto payload = new CreateActivationRequestDto(
                "E2E-EXT-001", "CUST-E2E", "INTERNET-500");

        CreationResult result = service.createRequest(payload);
        UUID requestId = result.request().getId();

        assertThat(result.isRetry()).isFalse();
        assertThat(result.request().getStatus()).isEqualTo(RequestStatus.PENDING);

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    var entity = repository.findById(requestId).orElseThrow();
                    assertThat(entity.getStatus()).isEqualTo(RequestStatus.COMPLETED);
                    assertThat(entity.getFailureReason()).isNull();
                });

        wireMockServer.verify(postRequestedFor(urlEqualTo("/provider/v1/activate"))
                .withHeader("Idempotency-Key", equalTo("E2E-EXT-001")));
    }
}
