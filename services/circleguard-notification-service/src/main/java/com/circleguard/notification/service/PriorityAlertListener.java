package com.circleguard.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriorityAlertListener {

    private final ObjectMapper objectMapper;
    private final TemplateService templateService;
    private final AuthAdminClient authAdminClient;

    @KafkaListener(topics = "alert.priority", groupId = "notification-priority-group")
    public void handlePriorityAlert(String message) {
        log.info("Received alert.priority event: {}", message);
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            String eventType = (String) payload.get("eventType");
            Integer affectedCount = (Integer) payload.get("affectedCount");

            log.info("Processing {} Priority Alert. Affected: {}", eventType, affectedCount);

            List<Map<String, String>> admins = authAdminClient.getPriorityAlertAdmins();

            if (!admins.isEmpty()) {
                for (Map<String, String> admin : admins) {
                    String email = admin.get("email");
                    String username = admin.get("username");
                    if (email != null && !email.isEmpty()) {
                        log.info("Dispatching priority alert to admin email: {}", email);
                        templateService.generateEmailContent(eventType, username);
                    }
                }
            } else {
                log.warn("No administrators found with alert:receive_priority permission.");
            }
        } catch (Exception e) {
            log.error("Failed to process alert.priority event: {}", e.getMessage(), e);
        }
    }
}
