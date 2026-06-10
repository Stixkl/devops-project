package com.circleguard.dashboard.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardMetricsTest {
    private MeterRegistry registry;
    private DashboardMetrics dashboardMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        dashboardMetrics = new DashboardMetrics(registry);
    }

    @Test
    void shouldIncrementAnalyticsQueries() {
        dashboardMetrics.recordAnalyticsQuery();
        assertEquals(1.0, registry.counter("dashboard.analytics.queries").count());
    }

    @Test
    void shouldIncrementCacheHits() {
        dashboardMetrics.recordCacheHit();
        assertEquals(1.0, registry.counter("dashboard.cache.hits").count());
    }

    @Test
    void shouldIncrementCacheMisses() {
        dashboardMetrics.recordCacheMiss();
        assertEquals(1.0, registry.counter("dashboard.cache.misses").count());
    }

    @Test
    void shouldRecordMultipleMetrics() {
        dashboardMetrics.recordAnalyticsQuery();
        dashboardMetrics.recordAnalyticsQuery();
        dashboardMetrics.recordCacheHit();
        dashboardMetrics.recordCacheMiss();
        dashboardMetrics.recordCacheMiss();
        dashboardMetrics.recordCacheMiss();
        assertEquals(2.0, registry.counter("dashboard.analytics.queries").count());
        assertEquals(1.0, registry.counter("dashboard.cache.hits").count());
        assertEquals(3.0, registry.counter("dashboard.cache.misses").count());
    }
}
