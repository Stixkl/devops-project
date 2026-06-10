package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class IdentityClient {

    private final RestTemplate restTemplate;
    private final String identityServiceUrl;

    public IdentityClient(
            @Value("${circleguard.client.identity-service.url:http://localhost:8083}")
            String identityServiceUrl,
            @Value("${circleguard.client.identity-service.connect-timeout:2000}")
            int connectTimeout,
            @Value("${circleguard.client.identity-service.read-timeout:3000}")
            int readTimeout,
            @Value("${circleguard.client.identity-service.write-timeout:3000}")
            int writeTimeout) {
        this.identityServiceUrl = identityServiceUrl;
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(Math.max(readTimeout, writeTimeout)))
                .build();
    }

    @CircuitBreaker(name = "identityService", fallbackMethod = "fallbackMapIdentity")
    public Optional<IdentityMapping> mapIdentity(IdentityMappingRequest request) {
        Map response = restTemplate.postForObject(
                identityServiceUrl + "/api/v1/identities/map",
                request,
                Map.class
        );
        if (response != null && response.containsKey("anonymousId")) {
            Object idObj = response.get("anonymousId");
            if (idObj == null) {
                return Optional.empty();
            }
            UUID anonymousId = UUID.fromString(idObj.toString());
            return Optional.of(new IdentityMapping(anonymousId));
        }
        return Optional.empty();
    }

    private Optional<IdentityMapping> fallbackMapIdentity(IdentityMappingRequest request, Throwable t) {
        log.warn("Identity service unavailable. Circuit OPEN. Reason: {}", t.getMessage());
        return Optional.empty();
    }
}
