package com.circleguard.dashboard.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DashboardMetrics {

    private final Counter analyticsQueries;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public DashboardMetrics(MeterRegistry registry) {
        this.analyticsQueries = Counter.builder("dashboard.analytics.queries")
            .description("Analytics queries executed").register(registry);
        this.cacheHits = Counter.builder("dashboard.cache.hits")
            .description("Fallback cache hits").register(registry);
        this.cacheMisses = Counter.builder("dashboard.cache.misses")
            .description("Fallback cache misses").register(registry);
    }

    public void recordAnalyticsQuery() { analyticsQueries.increment(); }
    public void recordCacheHit() { cacheHits.increment(); }
    public void recordCacheMiss() { cacheMisses.increment(); }
}
