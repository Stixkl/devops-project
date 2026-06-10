package com.circleguard.notification.observability;

import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CircuitBreakerEventLogger {

    private final MeterRegistry meterRegistry;

    public CircuitBreakerEventLogger(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    public void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String fromState = event.getStateTransition().getFromState().toString();
        String toState = event.getStateTransition().getToState().toString();
        String cbName = event.getCircuitBreakerName();

        log.warn(
                "Circuit Breaker '{}' transitioned from {} to {}",
                cbName, fromState, toState
        );

        Counter.builder("resilience4j.circuitbreaker.transitions")
            .tag("name", cbName)
            .tag("from", fromState)
            .tag("to", toState)
            .register(meterRegistry)
            .increment();
    }
}
