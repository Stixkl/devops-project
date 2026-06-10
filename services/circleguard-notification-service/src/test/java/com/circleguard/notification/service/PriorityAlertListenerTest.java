package com.circleguard.notification.service;

import com.circleguard.notification.client.AuthServiceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PriorityAlertListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private PriorityAlertListener priorityAlertListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testHandlePriorityAlert_Success() throws Exception {
        String message = "{\"eventType\":\"CONFIRMED_CASE\",\"affectedCount\":1}";
        Map<String, Object> payload = Map.of("eventType", "CONFIRMED_CASE", "affectedCount", 1);

        when(objectMapper.readValue(eq(message), any(TypeReference.class)))
            .thenReturn(payload);
        when(authServiceClient.getUsersByPermission("alert:receive_priority"))
            .thenReturn(List.of("admin1"));

        priorityAlertListener.handlePriorityAlert(message);

        verify(notificationDispatcher).dispatchToUsers(eq(payload), eq(List.of("admin1")));
    }

    @Test
    void testHandlePriorityAlert_NoAdmins() throws Exception {
        String message = "{\"eventType\":\"CONFIRMED_CASE\",\"affectedCount\":1}";
        Map<String, Object> payload = Map.of("eventType", "CONFIRMED_CASE", "affectedCount", 1);

        when(objectMapper.readValue(eq(message), any(TypeReference.class)))
            .thenReturn(payload);
        when(authServiceClient.getUsersByPermission("alert:receive_priority"))
            .thenReturn(null);

        priorityAlertListener.handlePriorityAlert(message);

        verify(notificationDispatcher, never()).dispatchToAllChannels(any());
        verify(notificationDispatcher, never()).dispatchToUsers(any(), anyList());
    }
}
