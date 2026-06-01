package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class PriorityAlertListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TemplateService templateService;

    @Mock
    private AuthAdminClient authAdminClient;

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

        when(objectMapper.readValue(eq(message), ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
            .thenReturn(payload);

        List<Map<String, String>> mockAdmins = List.of(
            Map.of("email", "admin@university.edu", "username", "admin1")
        );

        when(authAdminClient.getPriorityAlertAdmins()).thenReturn(mockAdmins);

        priorityAlertListener.handlePriorityAlert(message);

        verify(templateService, times(1)).generateEmailContent("CONFIRMED_CASE", "admin1");
    }

    @Test
    void testHandlePriorityAlert_NoAdmins() throws Exception {
        String message = "{\"eventType\":\"CONFIRMED_CASE\",\"affectedCount\":1}";
        Map<String, Object> payload = Map.of("eventType", "CONFIRMED_CASE", "affectedCount", 1);

        when(objectMapper.readValue(eq(message), ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
            .thenReturn(payload);

        when(authAdminClient.getPriorityAlertAdmins()).thenReturn(Collections.emptyList());

        priorityAlertListener.handlePriorityAlert(message);

        verify(templateService, never()).generateEmailContent(anyString(), anyString());
    }
}
