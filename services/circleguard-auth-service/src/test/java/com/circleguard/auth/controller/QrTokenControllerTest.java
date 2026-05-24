package com.circleguard.auth.controller;

import com.circleguard.auth.service.QrTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QrTokenController.class)
public class QrTokenControllerTest {

    private static final String VALID_UUID = "550e8400-e29b-41d4-a716-446655440000";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QrTokenService qrService;

    @Test
    @WithMockUser(username = VALID_UUID)
    void shouldGenerateQrTokenSuccessfully() throws Exception {
        UUID anonymousId = UUID.fromString(VALID_UUID);
        String qrToken = "mock-qr-token-12345";

        when(qrService.generateQrToken(anonymousId)).thenReturn(qrToken);

        mockMvc.perform(get("/api/v1/auth/qr/generate")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").value(qrToken))
                .andExpect(jsonPath("$.expiresIn").value("60"));
    }

    @Test
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/qr/generate"))
                .andExpect(status().isUnauthorized());
    }
}