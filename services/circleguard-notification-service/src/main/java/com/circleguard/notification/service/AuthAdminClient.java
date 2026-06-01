package com.circleguard.notification.service;

import com.circleguard.notification.config.NotificationProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class AuthAdminClient {

    private final RestTemplate restTemplate;
    private final String authApiUrl;

    public AuthAdminClient(RestTemplate authRestTemplate, NotificationProperties properties) {
        this.restTemplate = authRestTemplate;
        this.authApiUrl = properties.getApi().getUrl();
    }

    @CircuitBreaker(name = "auth", fallbackMethod = "getPriorityAlertAdminsFallback")
    @Retry(name = "auth")
    @SuppressWarnings("unchecked")
    public List<Map<String, String>> getPriorityAlertAdmins() {
        String url = authApiUrl + "/api/v1/users/permissions/alert:receive_priority";
        List<Map<String, String>> admins = restTemplate.getForObject(url, List.class);
        return admins != null ? admins : Collections.emptyList();
    }

    private List<Map<String, String>> getPriorityAlertAdminsFallback(Throwable t) {
        log.warn("Falling back: auth-service unavailable for priority-alert admins, returning empty list", t);
        return Collections.emptyList();
    }
}
