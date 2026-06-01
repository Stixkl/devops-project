package com.circleguard.auth.client;

import com.circleguard.auth.config.AuthClientProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Slf4j
public class IdentityClient {

    private final RestTemplate restTemplate;
    private final String identityUrl;

    public IdentityClient(RestTemplate identityRestTemplate, AuthClientProperties properties) {
        this.restTemplate = identityRestTemplate;
        this.identityUrl = properties.getIdentityService().getUrl() + "/api/v1/identities/map";
    }

    @CircuitBreaker(name = "identity", fallbackMethod = "getAnonymousIdFallback")
    @Retry(name = "identity")
    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        Map response = restTemplate.postForObject(identityUrl, request, Map.class);
        return UUID.fromString(response.get("anonymousId").toString());
    }

    private UUID getAnonymousIdFallback(String realIdentity, Throwable t) {
        log.error("Falling back: identity-service unavailable for anonymous-id mapping", t);
        throw new IllegalStateException("Identity service unavailable", t);
    }
}
