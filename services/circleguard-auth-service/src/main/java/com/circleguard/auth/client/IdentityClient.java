package com.circleguard.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Component
public class IdentityClient {
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${IDENTITY_SERVICE_URL:http://localhost:8083}")
    private String identityServiceUrl;

    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        Map response = restTemplate.postForObject(
            identityServiceUrl + "/api/v1/identities/map", request, Map.class);
        return UUID.fromString(response.get("anonymousId").toString());
    }
}
