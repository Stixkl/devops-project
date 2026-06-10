package com.circleguard.gateway.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GatewayMetrics {

    private final Counter validationsTotal;
    private final Counter validationsAllowed;
    private final Counter validationsDenied;

    public GatewayMetrics(MeterRegistry registry) {
        this.validationsTotal = Counter.builder("gate.validations.total")
            .description("Total QR validations").register(registry);
        this.validationsAllowed = Counter.builder("gate.validations.allowed")
            .description("Access allowed").register(registry);
        this.validationsDenied = Counter.builder("gate.validations.denied")
            .description("Access denied").register(registry);
    }

    public void recordValidation() { validationsTotal.increment(); }
    public void recordAllowed() { validationsAllowed.increment(); }
    public void recordDenied() { validationsDenied.increment(); }
}
