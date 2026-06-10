package com.circleguard.notification.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationMetricsTest {
    private MeterRegistry registry;
    private NotificationMetrics notificationMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        notificationMetrics = new NotificationMetrics(registry);
    }

    @Test void shouldIncrementTotal() {
        notificationMetrics.recordSentTotal();
        assertEquals(1.0, registry.counter("notifications.sent.total").count()); }

    @Test void shouldIncrementEmail() {
        notificationMetrics.recordSentEmail();
        assertEquals(1.0, registry.counter("notifications.sent.email").count()); }

    @Test void shouldIncrementSms() {
        notificationMetrics.recordSentSms();
        assertEquals(1.0, registry.counter("notifications.sent.sms").count()); }

    @Test void shouldIncrementPush() {
        notificationMetrics.recordSentPush();
        assertEquals(1.0, registry.counter("notifications.sent.push").count()); }

    @Test void shouldIncrementBroadcast() {
        notificationMetrics.recordBroadcast();
        assertEquals(1.0, registry.counter("notifications.broadcast").count()); }

    @Test void shouldRecordMultipleMetrics() {
        notificationMetrics.recordSentTotal();
        notificationMetrics.recordSentEmail();
        notificationMetrics.recordSentSms();
        notificationMetrics.recordSentPush();
        notificationMetrics.recordBroadcast();
        assertEquals(1.0, registry.counter("notifications.sent.total").count());
        assertEquals(1.0, registry.counter("notifications.sent.email").count());
        assertEquals(1.0, registry.counter("notifications.sent.sms").count());
        assertEquals(1.0, registry.counter("notifications.sent.push").count());
        assertEquals(1.0, registry.counter("notifications.broadcast").count());
    }
}
