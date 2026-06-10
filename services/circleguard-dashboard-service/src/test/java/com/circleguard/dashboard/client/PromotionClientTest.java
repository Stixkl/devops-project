package com.circleguard.dashboard.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {PromotionClient.class, com.circleguard.dashboard.observability.DashboardMetrics.class})
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
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
