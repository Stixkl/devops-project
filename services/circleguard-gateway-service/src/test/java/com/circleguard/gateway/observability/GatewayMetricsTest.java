package com.circleguard.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayMetricsTest {
    private MeterRegistry registry;
    private GatewayMetrics gatewayMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        gatewayMetrics = new GatewayMetrics(registry);
    }

    @Test void shouldIncrementValidation() {
        gatewayMetrics.recordValidation();
        assertEquals(1.0, registry.counter("gate.validations.total").count()); }

    @Test void shouldIncrementAllowed() {
        gatewayMetrics.recordAllowed();
        assertEquals(1.0, registry.counter("gate.validations.allowed").count()); }

    @Test void shouldIncrementDenied() {
        gatewayMetrics.recordDenied();
        assertEquals(1.0, registry.counter("gate.validations.denied").count()); }

    @Test void shouldRecordMultiple() {
        gatewayMetrics.recordValidation();
        gatewayMetrics.recordAllowed();
        gatewayMetrics.recordDenied();
        gatewayMetrics.recordDenied();
        assertEquals(1.0, registry.counter("gate.validations.total").count());
        assertEquals(1.0, registry.counter("gate.validations.allowed").count());
        assertEquals(2.0, registry.counter("gate.validations.denied").count());
    }
}
