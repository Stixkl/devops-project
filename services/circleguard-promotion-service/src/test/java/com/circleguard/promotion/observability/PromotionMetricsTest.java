package com.circleguard.promotion.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PromotionMetricsTest {
    private MeterRegistry registry;
    private PromotionMetrics promotionMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        promotionMetrics = new PromotionMetrics(registry);
    }

    @Test
    void shouldIncrementHealthReports() {
        promotionMetrics.recordHealthReport();
        assertEquals(1.0, registry.counter("promotion.health.reports").count());
    }

    @Test
    void shouldIncrementEncounters() {
        promotionMetrics.recordEncounterReported();
        assertEquals(1.0, registry.counter("promotion.encounters.reported").count());
    }

    @Test
    void shouldIncrementCircles() {
        promotionMetrics.recordCircleCreated();
        assertEquals(1.0, registry.counter("promotion.circles.created").count());
    }

    @Test
    void shouldIncrementFencingEvents() {
        promotionMetrics.recordFencingEvent();
        assertEquals(1.0, registry.counter("promotion.fencing.events").count());
    }

    @Test
    void shouldSetActiveCases() {
        promotionMetrics.setActiveCases(5);
        double gaugeValue = registry.find("promotion.active.cases").gauge().value();
        assertEquals(5.0, gaugeValue);
    }

    @Test
    void shouldRecordMultipleMetrics() {
        promotionMetrics.recordHealthReport();
        promotionMetrics.recordHealthReport();
        promotionMetrics.recordEncounterReported();
        promotionMetrics.recordCircleCreated();
        promotionMetrics.recordFencingEvent();
        promotionMetrics.recordFencingEvent();
        promotionMetrics.setActiveCases(3);
        assertEquals(2.0, registry.counter("promotion.health.reports").count());
        assertEquals(1.0, registry.counter("promotion.encounters.reported").count());
        assertEquals(1.0, registry.counter("promotion.circles.created").count());
        assertEquals(2.0, registry.counter("promotion.fencing.events").count());
        assertEquals(3.0, registry.find("promotion.active.cases").gauge().value());
    }
}
