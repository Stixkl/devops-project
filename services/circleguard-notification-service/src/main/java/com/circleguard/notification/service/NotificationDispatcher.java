package com.circleguard.notification.service;

import com.circleguard.notification.observability.NotificationMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final EmailService emailService;
    private final SmsService smsService;
    private final PushService pushService;
    private final TemplateService templateService;
    private final NotificationMetrics notificationMetrics;

    public void dispatch(String userId, String status) {
        log.info("Dispatching contextual multi-channel notifications for user: {} with status: {}", userId, status);

        String emailContent = templateService.generateEmailContent(status, userId);
        String pushContent = templateService.generatePushContent(status);
        Map<String, String> pushMetadata = templateService.generatePushMetadata(status);
        String smsContent = templateService.generateSmsContent(status);

        notificationMetrics.recordSentTotal();

        CompletableFuture.allOf(
            emailService.sendAsync(userId, emailContent).thenRun(notificationMetrics::recordSentEmail),
            smsService.sendAsync(userId, smsContent).thenRun(notificationMetrics::recordSentSms),
            pushService.sendAsync(userId, pushContent, pushMetadata).thenRun(notificationMetrics::recordSentPush)
        ).handle((result, ex) -> {
            if (ex != null) {
                log.error("Error during multi-channel dispatch for user {}: {}", userId, ex.getMessage());
            } else {
                log.info("Multi-channel dispatch completed successfully for user: {}", userId);
            }
            return result;
        });
    }

    public void dispatchToAllChannels(Map<String, Object> event) {
        String eventType = (String) event.getOrDefault("eventType", "UNKNOWN");
        log.warn("Broadcasting priority alert '{}' to all channels (no user filter)", eventType);

        String emailContent = templateService.generateEmailContent(eventType, null);
        String pushContent = templateService.generatePushContent(eventType);
        Map<String, String> pushMetadata = templateService.generatePushMetadata(eventType);
        String smsContent = templateService.generateSmsContent(eventType);

        String broadcastId = "BROADCAST_" + eventType;

        notificationMetrics.recordSentTotal();
        notificationMetrics.recordBroadcast();

        CompletableFuture.allOf(
            emailService.sendAsync(broadcastId, emailContent).thenRun(notificationMetrics::recordSentEmail),
            smsService.sendAsync(broadcastId, smsContent).thenRun(notificationMetrics::recordSentSms),
            pushService.sendAsync(broadcastId, pushContent, pushMetadata).thenRun(notificationMetrics::recordSentPush)
        ).handle((result, ex) -> {
            if (ex != null) {
                log.error("Error during broadcast dispatch for event '{}': {}", eventType, ex.getMessage());
            } else {
                log.info("Broadcast dispatch completed successfully for event: {}", eventType);
            }
            return result;
        });
    }

    public void dispatchToUsers(Map<String, Object> event, List<String> userIds) {
        String eventType = (String) event.getOrDefault("eventType", "UNKNOWN");
        log.info("Dispatching priority alert '{}' to {} users", eventType, userIds.size());

        for (String userId : userIds) {
            dispatch(userId, eventType);
        }
    }
}
