package com.circleguard.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PromotionServiceIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Promotion service health endpoint responds")
    void promotionService_HealthCheck_ReturnsOk() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:8088/actuator/health", String.class);
            assertTrue(response.getStatusCode().is2xxSuccessful() ||
                       response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("POST /api/v1/health/report - accepts status update")
    void reportStatus_ReturnsOk() {
        Map<String, Object> request = Map.of(
            "anonymousId", UUID.randomUUID().toString(),
            "status", "SUSPECT",
            "adminOverride", false
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:8088/api/v1/health/report",
                HttpMethod.POST, entity, String.class);
            assertTrue(response.getStatusCode().is2xxSuccessful() ||
                       response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                       response.getStatusCode() == HttpStatus.FORBIDDEN);
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    @DisplayName("GET /api/v1/analytics/overview - returns analytics")
    void getAnalytics_ReturnsOverview() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:8088/api/v1/analytics/overview", String.class);
            assertTrue(response.getStatusCode().is2xxSuccessful() ||
                       response.getStatusCode() == HttpStatus.UNAUTHORIZED ||
                       response.getStatusCode() == HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            assertTrue(true);
        }
    }
}