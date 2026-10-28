package com.activation.engine.infrastructure.client;

import com.activation.engine.infrastructure.client.dto.ProviderActivationRequest;
import com.activation.engine.infrastructure.client.dto.ProviderActivationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExternalProviderClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalProviderClient.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;

    public ExternalProviderClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @CircuitBreaker(name = "providerClient")
    @Retry(name = "providerClient")
    public ProviderActivationResponse activateProduct(String externalId, String customerId, String productCode) {
        log.info("Sending activation request to external provider for externalId: {}", externalId);

        ProviderActivationRequest requestPayload = new ProviderActivationRequest(customerId, productCode);

        return restClient.post()
                .uri("/provider/v1/activate")
                .header(IDEMPOTENCY_KEY_HEADER, externalId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestPayload)
                .retrieve()
                .body(ProviderActivationResponse.class);
    }
}
