package com.circleguard.form.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FormMetricsTest {
    private MeterRegistry registry;
    private FormMetrics formMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        formMetrics = new FormMetrics(registry);
    }

    @Test
    void shouldIncrementSurveySubmitted() {
        formMetrics.recordSurveySubmitted();
        assertEquals(1.0, registry.counter("form.surveys.submitted").count());
    }

    @Test
    void shouldIncrementCertificateValidated() {
        formMetrics.recordCertificateValidated();
        assertEquals(1.0, registry.counter("form.certificates.validated").count());
    }

    @Test
    void shouldSetActiveQuestionnaires() {
        formMetrics.setActiveQuestionnaires(3);
        assertEquals(3.0, registry.find("form.questionnaires.active").gauge().value());
    }
}
