package com.circleguard.dashboard.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {PromotionClient.class, PromotionClientTest.TestConfig.class})
@TestPropertySource(properties = {
    "circleguard.client.promotion-service.url=http://localhost:18088",
    "circleguard.client.promotion-service.connect-timeout=100",
    "circleguard.client.promotion-service.read-timeout=100",
    "resilience4j.circuitbreaker.instances.promotionService.minimum-number-of-calls=1",
    "resilience4j.circuitbreaker.instances.promotionService.sliding-window-size=1",
    "resilience4j.circuitbreaker.instances.promotionService.wait-duration-in-open-state=1s"
})
class PromotionClientTest {

    @Autowired
    private PromotionClient promotionClient;

    @Configuration
    @EnableCircuitBreaker
    @ImportAutoConfiguration(Resilience4JAutoConfiguration.class)
    static class TestConfig {
    }

    @Test
    void shouldReturnServiceUnavailableWhenNoCacheAndCircuitOpen() {
        // First call fails, circuit opens
        try {
            promotionClient.getHealthStats();
        } catch (Exception ignored) {
        }

        Map<String, Object> result = promotionClient.getHealthStats();

        assertNotNull(result);
        assertEquals("Service unavailable", result.get("error"));
        assertEquals(false, result.get("cached"));
        assertFalse(result.containsKey("cached_at"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenNoCacheForDepartment() {
        try {
            promotionClient.getHealthStatsByDepartment("CS");
        } catch (Exception ignored) {
        }

        Map<String, Object> result = promotionClient.getHealthStatsByDepartment("CS");

        assertNotNull(result);
        assertEquals("Service unavailable", result.get("error"));
        assertEquals(false, result.get("cached"));
        assertFalse(result.containsKey("cached_at"));
    }

    @Test
    void fallbackResultShouldNotContainSensitiveInfo() {
        try {
            promotionClient.getHealthStats();
        } catch (Exception ignored) {
        }

        Map<String, Object> result = promotionClient.getHealthStats();

        assertNotNull(result);
        assertFalse(result.containsKey("stackTrace"));
        assertFalse(result.containsKey("exception"));
        assertFalse(result.containsKey("cause"));
    }
}
