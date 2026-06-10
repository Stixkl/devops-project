package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AuditLogService auditLogService;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender, auditLogService);
    }

    @Test
    @DisplayName("Should send email asynchronously")
    void sendAsync_ShouldReturnCompletableFuture() {
        String userId = "user123";
        String content = "Test email content";

        CompletableFuture<Void> result = emailService.sendAsync(userId, content);

        assertNotNull(result);
        assertFalse(result.isCompletedExceptionally());
    }

    @Test
    @DisplayName("Should complete successfully for valid inputs")
    void sendAsync_WithValidInputs_CompletesSuccessfully() {
        String userId = "user456";
        String content = "Health alert content";

        CompletableFuture<Void> result = emailService.sendAsync(userId, content);

        assertDoesNotThrow(() -> result.get());
    }

    @Test
    @DisplayName("Should handle empty content gracefully")
    void sendAsync_WithEmptyContent_Completes() {
        String userId = "user789";
        String content = "";

        CompletableFuture<Void> result = emailService.sendAsync(userId, content);

        assertDoesNotThrow(() -> result.get());
    }
}