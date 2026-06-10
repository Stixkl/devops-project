package com.circleguard.form.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FormMetrics {

    private final Counter surveysSubmitted;
    private final Counter certificatesValidated;
    private final AtomicInteger activeQuestionnaires;

    public FormMetrics(MeterRegistry registry) {
        this.surveysSubmitted = Counter.builder("form.surveys.submitted")
            .description("Health surveys submitted").register(registry);
        this.certificatesValidated = Counter.builder("form.certificates.validated")
            .description("Certificates validated/rejected").register(registry);
        this.activeQuestionnaires = new AtomicInteger(0);
        Gauge.builder("form.questionnaires.active", activeQuestionnaires, AtomicInteger::get)
            .description("Currently active questionnaires").register(registry);
    }

    public void recordSurveySubmitted() { surveysSubmitted.increment(); }
    public void recordCertificateValidated() { certificatesValidated.increment(); }
    public void setActiveQuestionnaires(int count) { activeQuestionnaires.set(count); }
}
