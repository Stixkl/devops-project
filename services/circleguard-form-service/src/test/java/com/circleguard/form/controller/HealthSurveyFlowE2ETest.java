package com.circleguard.form.controller;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthSurveyFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HealthSurveyRepository surveyRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setup() {
        surveyRepository.deleteAll();
    }

    @Test
    void submitSurvey_WithNoSymptoms_SavesAndReturnsId() {
        UUID anonymousId = UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "anonymousId", anonymousId.toString(),
                "hasFever", false,
                "hasCough", false
        );

        ResponseEntity<HealthSurvey> response = restTemplate.postForEntity(
                "/api/v1/surveys",
                new HttpEntity<>(body, jsonHeaders()),
                HealthSurvey.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(surveyRepository.count()).isEqualTo(1);
    }

    @Test
    void submitSurvey_WithFeverSymptom_SetsFeverFlag() {
        UUID anonymousId = UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "anonymousId", anonymousId.toString(),
                "hasFever", true,
                "hasCough", false
        );

        ResponseEntity<HealthSurvey> response = restTemplate.postForEntity(
                "/api/v1/surveys",
                new HttpEntity<>(body, jsonHeaders()),
                HealthSurvey.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getHasFever()).isTrue();
    }

    @Test
    void submitSurvey_WithAttachmentPath_SetsPendingValidation() {
        UUID anonymousId = UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "anonymousId", anonymousId.toString(),
                "attachmentPath", "/uploads/certificate.pdf"
        );

        ResponseEntity<HealthSurvey> response = restTemplate.postForEntity(
                "/api/v1/surveys",
                new HttpEntity<>(body, jsonHeaders()),
                HealthSurvey.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getValidationStatus()).isNotNull();
    }

    @Test
    void submitMultipleSurveys_EachPersisted() {
        for (int i = 0; i < 3; i++) {
            Map<String, Object> body = Map.of(
                    "anonymousId", UUID.randomUUID().toString(),
                    "hasFever", false,
                    "hasCough", false
            );
            restTemplate.postForEntity(
                    "/api/v1/surveys",
                    new HttpEntity<>(body, jsonHeaders()),
                    HealthSurvey.class
            );
        }

        assertThat(surveyRepository.count()).isEqualTo(3);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
