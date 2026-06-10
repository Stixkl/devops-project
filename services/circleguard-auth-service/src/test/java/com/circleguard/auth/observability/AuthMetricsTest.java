package com.circleguard.auth.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthMetricsTest {

    private MeterRegistry registry;
    private AuthMetrics authMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        authMetrics = new AuthMetrics(registry);
    }

    @Test
    void shouldIncrementLoginSuccess() {
        authMetrics.recordLoginSuccess();
        double count = registry.counter("auth.logins.total").count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldIncrementLoginFailure() {
        authMetrics.recordLoginFailure();
        double count = registry.counter("auth.logins.failed").count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldIncrementLoginDegraded() {
        authMetrics.recordLoginDegraded();
        double count = registry.counter("auth.logins.degraded").count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldIncrementQrGenerated() {
        authMetrics.recordQrGenerated();
        double count = registry.counter("auth.qr.generated").count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldRecordMultipleMetrics() {
        authMetrics.recordLoginSuccess();
        authMetrics.recordLoginSuccess();
        authMetrics.recordLoginFailure();
        authMetrics.recordLoginDegraded();
        authMetrics.recordQrGenerated();
        authMetrics.recordQrGenerated();
        authMetrics.recordQrGenerated();

        assertEquals(2.0, registry.counter("auth.logins.total").count());
        assertEquals(1.0, registry.counter("auth.logins.failed").count());
        assertEquals(1.0, registry.counter("auth.logins.degraded").count());
        assertEquals(3.0, registry.counter("auth.qr.generated").count());
    }
}
