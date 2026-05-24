package com.circleguard.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PushServiceImplTest {

    @Mock
    private TemplateService templateService;

    @Test
    @DisplayName("Should send push notification asynchronously")
    void sendAsync_ShouldReturnCompletableFuture() {
        PushServiceImpl pushService = new PushServiceImpl(templateService);
        String userId = "user123";
        String content = "Alert content";
        Map<String, String> metadata = Map.of("priority", "high");

        CompletableFuture<Void> result = pushService.sendAsync(userId, content, metadata);

        assertNotNull(result);
        assertFalse(result.isCompletedExceptionally());
    }

    @Test
    @DisplayName("Should complete successfully for valid push notification")
    void sendAsync_WithValidData_CompletesSuccessfully() {
        PushServiceImpl pushService = new PushServiceImpl(templateService);
        String userId = "user456";
        String content = "Health status update";
        Map<String, String> metadata = Map.of("channel", "alerts");

        CompletableFuture<Void> result = pushService.sendAsync(userId, content, metadata);

        assertDoesNotThrow(() -> result.get());
    }

    @Test
    @DisplayName("Should handle empty metadata gracefully")
    void sendAsync_WithEmptyMetadata_Completes() {
        PushServiceImpl pushService = new PushServiceImpl(templateService);
        String userId = "user789";
        String content = "Simple notification";
        Map<String, String> metadata = Map.of();

        CompletableFuture<Void> result = pushService.sendAsync(userId, content, metadata);

        assertDoesNotThrow(() -> result.get());
    }
}