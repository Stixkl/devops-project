package com.circleguard.notification.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Counter sentTotal;
    private final Counter sentEmail;
    private final Counter sentSms;
    private final Counter sentPush;
    private final Counter broadcast;

    public NotificationMetrics(MeterRegistry registry) {
        this.sentTotal = Counter.builder("notifications.sent.total")
            .description("Total notifications sent").register(registry);
        this.sentEmail = Counter.builder("notifications.sent.email")
            .description("Notifications sent via email").register(registry);
        this.sentSms = Counter.builder("notifications.sent.sms")
            .description("Notifications sent via SMS").register(registry);
        this.sentPush = Counter.builder("notifications.sent.push")
            .description("Notifications sent via push").register(registry);
        this.broadcast = Counter.builder("notifications.broadcast")
            .description("Notifications in broadcast mode (fallback)").register(registry);
    }

    public void recordSentTotal() { sentTotal.increment(); }
    public void recordSentEmail() { sentEmail.increment(); }
    public void recordSentSms() { sentSms.increment(); }
    public void recordSentPush() { sentPush.increment(); }
    public void recordBroadcast() { broadcast.increment(); }
}
