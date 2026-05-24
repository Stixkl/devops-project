package com.circleguard.gateway.controller;

import com.circleguard.gateway.service.QrValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GateController.class)
public class GateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QrValidationService validationService;

    @Test
    void shouldReturnValidationResultForGreenStatus() throws Exception {
        String token = "mock-token";
        QrValidationService.ValidationResult mockResult =
            new QrValidationService.ValidationResult(true, "GREEN", "Welcome to Campus");

        Mockito.when(validationService.validateToken(token)).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("GREEN"))
                .andExpect(jsonPath("$.message").value("Welcome to Campus"));
    }

    @Test
    void shouldReturnValidationResultForRedStatusDueToHealthRisk() throws Exception {
        String token = "mock-token";
        QrValidationService.ValidationResult mockResult =
            new QrValidationService.ValidationResult(false, "RED", "Access Denied: Health Risk Detected");

        Mockito.when(validationService.validateToken(token)).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("RED"))
                .andExpect(jsonPath("$.message").value("Access Denied: Health Risk Detected"));
    }

    @Test
    void shouldReturnRedForInvalidToken() throws Exception {
        String token = "invalid-token";
        QrValidationService.ValidationResult mockResult =
            new QrValidationService.ValidationResult(false, "RED", "Invalid or Expired Token");

        Mockito.when(validationService.validateToken(token)).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("RED"));
    }

    @Test
    void shouldReturnBadRequestWhenTokenMissing() throws Exception {
        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldHandleNullTokenGracefully() throws Exception {
        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": null}"))
                .andExpect(status().isBadRequest());
    }
}
