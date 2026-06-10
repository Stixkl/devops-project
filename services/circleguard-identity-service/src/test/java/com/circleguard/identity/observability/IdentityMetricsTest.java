package com.circleguard.identity.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentityMetricsTest {
    private MeterRegistry registry;
    private IdentityMetrics identityMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        identityMetrics = new IdentityMetrics(registry);
    }

    @Test
    void shouldIncrementMappingCreated() {
        identityMetrics.recordMappingCreated();
        assertEquals(1.0, registry.counter("identity.mappings.created").count());
    }

    @Test
    void shouldIncrementVisitorRegistered() {
        identityMetrics.recordVisitorRegistered();
        assertEquals(1.0, registry.counter("identity.visitors.registered").count());
    }

    @Test
    void shouldIncrementLookupExecuted() {
        identityMetrics.recordLookupExecuted();
        assertEquals(1.0, registry.counter("identity.lookups.executed").count());
    }

    @Test
    void shouldRecordMultipleMetrics() {
        identityMetrics.recordMappingCreated();
        identityMetrics.recordMappingCreated();
        identityMetrics.recordVisitorRegistered();
        identityMetrics.recordLookupExecuted();
        identityMetrics.recordLookupExecuted();
        identityMetrics.recordLookupExecuted();
        assertEquals(2.0, registry.counter("identity.mappings.created").count());
        assertEquals(1.0, registry.counter("identity.visitors.registered").count());
        assertEquals(3.0, registry.counter("identity.lookups.executed").count());
    }
}
