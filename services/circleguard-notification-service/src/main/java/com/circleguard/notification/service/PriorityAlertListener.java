package com.circleguard.notification.service;

import com.circleguard.notification.client.AuthServiceClient;
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
    private final AuthServiceClient authServiceClient;
    private final NotificationDispatcher notificationDispatcher;

    @KafkaListener(topics = "alert.priority", groupId = "notification-priority-group")
    public void handlePriorityAlert(String message) {
        log.info("Received alert.priority event: {}", message);
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
            Object eventTypeObj = payload.get("eventType");
            String eventType = eventTypeObj instanceof String ? (String) eventTypeObj : null;
            Object affectedCountObj = payload.get("affectedCount");
            Integer affectedCount = affectedCountObj instanceof Number ? ((Number) affectedCountObj).intValue() : null;

            log.info("Processing {} Priority Alert. Affected: {}", eventType, affectedCount);

            List<String> userIds = authServiceClient.getUsersByPermission("alert:receive_priority");

            if (userIds.size() == 1 && "BROADCAST_ALL".equals(userIds.get(0))) {
                log.warn("Auth service unavailable — sending priority alert via broadcast to all channels");
                notificationDispatcher.dispatchToAllChannels(payload);
            } else if (userIds != null && !userIds.isEmpty()) {
                notificationDispatcher.dispatchToUsers(payload, userIds);
            } else {
                log.warn("No administrators found with alert:receive_priority permission.");
            }
        } catch (Exception e) {
            log.error("Failed to process alert.priority event: {}", e.getMessage(), e);
        }
    }
}
