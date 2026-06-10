package com.circleguard.promotion.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PromotionMetrics {

    private final Counter healthReports;
    private final Counter encountersReported;
    private final Counter circlesCreated;
    private final Counter fencingEvents;
    private final AtomicInteger activeCases;

    public PromotionMetrics(MeterRegistry registry) {
        this.healthReports = Counter.builder("promotion.health.reports")
            .description("Health reports received").register(registry);
        this.encountersReported = Counter.builder("promotion.encounters.reported")
            .description("BLE encounters reported").register(registry);
        this.circlesCreated = Counter.builder("promotion.circles.created")
            .description("Circles created").register(registry);
        this.fencingEvents = Counter.builder("promotion.fencing.events")
            .description("Epidemiological fencing events").register(registry);
        this.activeCases = new AtomicInteger(0);
        Gauge.builder("promotion.active.cases", activeCases, AtomicInteger::get)
            .description("Currently active cases").register(registry);
    }

    public void recordHealthReport() { healthReports.increment(); }
    public void recordEncounterReported() { encountersReported.increment(); }
    public void recordCircleCreated() { circlesCreated.increment(); }
    public void recordFencingEvent() { fencingEvents.increment(); }
    public void setActiveCases(int count) { activeCases.set(count); }
}
