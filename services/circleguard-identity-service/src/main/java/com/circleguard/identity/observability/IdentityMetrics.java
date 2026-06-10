package com.circleguard.identity.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class IdentityMetrics {

    private final Counter mappingsCreated;
    private final Counter visitorsRegistered;
    private final Counter lookupsExecuted;

    public IdentityMetrics(MeterRegistry registry) {
        this.mappingsCreated = Counter.builder("identity.mappings.created")
            .description("Identity mappings created")
            .register(registry);
        this.visitorsRegistered = Counter.builder("identity.visitors.registered")
            .description("Temporary visitors registered")
            .register(registry);
        this.lookupsExecuted = Counter.builder("identity.lookups.executed")
            .description("Identity lookups executed")
            .register(registry);
    }

    public void recordMappingCreated() { mappingsCreated.increment(); }
    public void recordVisitorRegistered() { visitorsRegistered.increment(); }
    public void recordLookupExecuted() { lookupsExecuted.increment(); }
}
