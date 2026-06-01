package com.circleguard.dashboard.client;

import com.circleguard.dashboard.config.DashboardProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@Slf4j
public class PromotionClient {

    private final RestTemplate restTemplate;
    private final String promotionServiceUrl;

    public PromotionClient(RestTemplate promotionRestTemplate, DashboardProperties properties) {
        this.restTemplate = promotionRestTemplate;
        this.promotionServiceUrl = properties.getPromotionService().getUrl();
    }

    @CircuitBreaker(name = "promotion", fallbackMethod = "getHealthStatsFallback")
    @Retry(name = "promotion")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHealthStats() {
        return restTemplate.getForObject(
                promotionServiceUrl + "/api/v1/health-status/stats",
                Map.class
        );
    }

    @CircuitBreaker(name = "promotion", fallbackMethod = "getHealthStatsByDepartmentFallback")
    @Retry(name = "promotion")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHealthStatsByDepartment(String department) {
        return restTemplate.getForObject(
                promotionServiceUrl + "/api/v1/health-status/stats/department/" + department,
                Map.class
        );
    }

    private Map<String, Object> getHealthStatsFallback(Throwable t) {
        log.error("Falling back: failed to fetch health stats from promotion-service", t);
        return Map.of("error", "Service unavailable", "timestamp", new Date());
    }

    private Map<String, Object> getHealthStatsByDepartmentFallback(String department, Throwable t) {
        log.error("Falling back: failed to fetch department stats from promotion-service", t);
        return Map.of("error", "Service unavailable", "department", department, "timestamp", new Date());
    }
}
