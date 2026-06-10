package com.circleguard.notification.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private final String authServiceUrl;

    public AuthServiceClient(
            @Value("${circleguard.client.auth-service.url:http://localhost:8180}")
            String authServiceUrl,
            @Value("${circleguard.client.auth-service.connect-timeout:2000}")
            int connectTimeout,
            @Value("${circleguard.client.auth-service.read-timeout:3000}")
            int readTimeout,
            @Value("${circleguard.client.auth-service.write-timeout:3000}")
            int writeTimeout) {
        this.authServiceUrl = authServiceUrl;
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(Math.max(readTimeout, writeTimeout)))
                .build();
    }

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "authServicePermissions", fallbackMethod = "fallbackGetUsersByPermission")
    public List<String> getUsersByPermission(String permissionName) {
        String url = authServiceUrl + "/api/v1/users/permissions/" + permissionName;
        return restTemplate.getForObject(url, List.class);
    }

    private List<String> fallbackGetUsersByPermission(String permissionName, Throwable t) {
        log.warn("Auth service unavailable for permission '{}'. Forcing broadcast. Error: {}",
                permissionName, t.getMessage());
        return List.of("BROADCAST_ALL");
    }
}
