package com.circleguard.dashboard.client;

import com.circleguard.dashboard.observability.DashboardMetrics;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PromotionClient {

    private static final String CACHE_KEY_GLOBAL = "global-stats";
    private static final String CACHE_KEY_DEPT_PREFIX = "dept-stats:";

    private final RestTemplate restTemplate;
    private final String promotionServiceUrl;
    private final DashboardMetrics dashboardMetrics;
    private final Cache<String, Map> lastSuccessCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    public PromotionClient(
            DashboardMetrics dashboardMetrics,
            @Value("${circleguard.client.promotion-service.url:http://localhost:8088}")
            String promotionServiceUrl,
            @Value("${circleguard.client.promotion-service.connect-timeout:2000}")
            int connectTimeout,
            @Value("${circleguard.client.promotion-service.read-timeout:5000}")
            int readTimeout,
            @Value("${circleguard.client.promotion-service.write-timeout:3000}")
            int writeTimeout) {
        this.dashboardMetrics = dashboardMetrics;
        this.promotionServiceUrl = promotionServiceUrl;
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(Math.max(readTimeout, writeTimeout)))
                .build();
    }

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "promotionService", fallbackMethod = "fallbackGetHealthStats")
    public Map<String, Object> getHealthStats() {
        Map result = restTemplate.getForObject(
                promotionServiceUrl + "/api/v1/health-status/stats",
                Map.class
        );
        if (result != null) {
            lastSuccessCache.put(CACHE_KEY_GLOBAL, Map.copyOf(result));
        }
        dashboardMetrics.recordAnalyticsQuery();
        return result;
    }

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "promotionService", fallbackMethod = "fallbackGetHealthStatsByDepartment")
    public Map<String, Object> getHealthStatsByDepartment(String department) {
        Map result = restTemplate.getForObject(
                promotionServiceUrl + "/api/v1/health-status/stats/department/" + department,
                Map.class
        );
        if (result != null) {
            lastSuccessCache.put(CACHE_KEY_DEPT_PREFIX + department, Map.copyOf(result));
        }
        dashboardMetrics.recordAnalyticsQuery();
        return result;
    }

    private Map<String, Object> fallbackGetHealthStatsByDepartment(String department, Throwable t) {
        String cacheKey = department != null
                ? CACHE_KEY_DEPT_PREFIX + department
                : CACHE_KEY_GLOBAL;
        if (lastSuccessCache != null) {
            Map cached = lastSuccessCache.getIfPresent(cacheKey);
            if (cached != null) {
                if (dashboardMetrics != null) {
                    dashboardMetrics.recordCacheHit();
                }
                log.warn("Promotion service unavailable. Returning cached data for key: {}", cacheKey);
                Map<String, Object> result = new HashMap<>(cached);
                result.put("cached", true);
                result.put("cached_at", Instant.now().toString());
                return Map.copyOf(result);
            }
        }
        if (dashboardMetrics != null) {
            dashboardMetrics.recordCacheMiss();
        }
        log.warn("Promotion service unavailable. No cached data for key: {}", cacheKey);
        return Map.of("error", "Service unavailable", "cached", false);
    }

    private Map<String, Object> fallbackGetHealthStats(Throwable t) {
        return fallbackGetHealthStatsByDepartment(null, t);
    }
}
