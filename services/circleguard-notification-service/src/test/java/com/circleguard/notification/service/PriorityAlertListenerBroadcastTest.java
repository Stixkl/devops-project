package com.circleguard.notification.service;

import com.circleguard.notification.client.AuthServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriorityAlertListenerBroadcastTest {

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private PriorityAlertListener listener;

    @Test
    void shouldDispatchToAllChannelsWhenBroadcastAll() {
        when(authServiceClient.getUsersByPermission(anyString()))
                .thenReturn(List.of("BROADCAST_ALL"));

        listener.handlePriorityAlert("{\"eventType\":\"TEST\",\"affectedCount\":5}");

        verify(notificationDispatcher).dispatchToAllChannels(any(Map.class));
        verify(notificationDispatcher, never()).dispatchToUsers(anyList());
    }

    @Test
    void shouldDispatchToUsersWhenUserIdsReturned() {
        when(authServiceClient.getUsersByPermission(anyString()))
                .thenReturn(List.of("user1", "user2"));

        listener.handlePriorityAlert("{\"eventType\":\"TEST\",\"affectedCount\":5}");

        verify(notificationDispatcher).dispatchToUsers(any(Map.class), eq(List.of("user1", "user2")));
        verify(notificationDispatcher, never()).dispatchToAllChannels(any(Map.class));
    }

    @Test
    void shouldHandleEmptyUserList() {
        when(authServiceClient.getUsersByPermission(anyString()))
                .thenReturn(List.of());

        listener.handlePriorityAlert("{\"eventType\":\"TEST\",\"affectedCount\":5}");

        verify(notificationDispatcher, never()).dispatchToAllChannels(any(Map.class));
        verify(notificationDispatcher, never()).dispatchToUsers(any(Map.class), anyList());
    }
}
