package com.circleguard.auth.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final Counter loginsTotal;
    private final Counter loginsFailed;
    private final Counter loginsDegraded;
    private final Counter qrGenerated;

    public AuthMetrics(MeterRegistry registry) {
        this.loginsTotal = Counter.builder("auth.logins.total")
            .description("Total successful logins")
            .register(registry);
        this.loginsFailed = Counter.builder("auth.logins.failed")
            .description("Total failed logins")
            .register(registry);
        this.loginsDegraded = Counter.builder("auth.logins.degraded")
            .description("Logins in degraded mode (circuit breaker open)")
            .register(registry);
        this.qrGenerated = Counter.builder("auth.qr.generated")
            .description("QR tokens generated")
            .register(registry);
    }

    public void recordLoginSuccess() { loginsTotal.increment(); }
    public void recordLoginFailure() { loginsFailed.increment(); }
    public void recordLoginDegraded() { loginsDegraded.increment(); }
    public void recordQrGenerated() { qrGenerated.increment(); }
}
