package com.circleguard.dashboard.observability;

import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CircuitBreakerEventLogger {

    @EventListener
    public void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn(
                "Circuit Breaker '{}' transitioned from {} to {}",
                event.getCircuitBreakerName(),
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState()
        );
    }
}
