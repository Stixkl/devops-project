package com.circleguard.notification.service;

import com.circleguard.notification.client.AuthServiceClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriorityAlertListenerBroadcastTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private com.circleguard.notification.observability.NotificationMetrics notificationMetrics;

    @InjectMocks
    private PriorityAlertListener listener;

    @Test
    void shouldDispatchToAllChannelsWhenBroadcastAll() throws Exception {
        doReturn(Map.of("eventType", "TEST", "affectedCount", 5))
                .when(objectMapper).readValue(anyString(), any(TypeReference.class));
        when(authServiceClient.getUsersByPermission(anyString()))
                .thenReturn(List.of("BROADCAST_ALL"));

        listener.handlePriorityAlert("{\"eventType\":\"TEST\",\"affectedCount\":5}");

        verify(notificationDispatcher).dispatchToAllChannels(any(Map.class));
        verify(notificationDispatcher, never()).dispatchToUsers(any(Map.class), anyList());
    }

    @Test
    void shouldDispatchToUsersWhenUserIdsReturned() throws Exception {
        doReturn(Map.of("eventType", "TEST", "affectedCount", 5))
                .when(objectMapper).readValue(anyString(), any(TypeReference.class));
        when(authServiceClient.getUsersByPermission(anyString()))
                .thenReturn(List.of("user1", "user2"));

        listener.handlePriorityAlert("{\"eventType\":\"TEST\",\"affectedCount\":5}");

        verify(notificationDispatcher).dispatchToUsers(any(Map.class), eq(List.of("user1", "user2")));
        verify(notificationDispatcher, never()).dispatchToAllChannels(any(Map.class));
    }

    @Test
    void shouldHandleEmptyUserList() throws Exception {
        doReturn(Map.of("eventType", "TEST", "affectedCount", 5))
                .when(objectMapper).readValue(anyString(), any(TypeReference.class));
        when(authServiceClient.getUsersByPermission(anyString()))
                .thenReturn(List.of());

        listener.handlePriorityAlert("{\"eventType\":\"TEST\",\"affectedCount\":5}");

        verify(notificationDispatcher, never()).dispatchToAllChannels(any(Map.class));
        verify(notificationDispatcher, never()).dispatchToUsers(any(Map.class), anyList());
    }
}
