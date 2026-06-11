package com.circleguard.auth.controller;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.client.IdentityMapping;
import com.circleguard.auth.client.IdentityMappingRequest;
import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
public class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authManager;

    @MockBean
    private JwtTokenService jwtService;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private com.circleguard.auth.observability.AuthMetrics authMetrics;

    @Test
    void shouldLoginSuccessfullyAndReturnAnonymizedToken() throws Exception {
        String username = "testuser";
        String password = "password123";
        UUID anonymousId = UUID.randomUUID();
        String token = "mock-jwt-token";

        Authentication auth = Mockito.mock(Authentication.class);
        Mockito.when(authManager.authenticate(Mockito.any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        Mockito.when(identityClient.mapIdentity(new IdentityMappingRequest(username)))
                .thenReturn(java.util.Optional.of(new IdentityMapping(anonymousId)));

        Mockito.when(jwtService.generateToken(Mockito.eq(anonymousId), Mockito.any(Authentication.class)))
                .thenReturn(token);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"testuser\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.mode").value("full"));
    }

    @Test
    void shouldRejectMalformedAnonymousIdOnVisitorHandoff() throws Exception {
        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousId\": \"test-anon-1718000000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGenerateVisitorHandoffForValidUuid() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        Mockito.when(jwtService.generateToken(Mockito.eq(anonymousId), Mockito.any(Authentication.class)))
                .thenReturn("visitor-token");

        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousId\": \"" + anonymousId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("visitor-token"))
                .andExpect(jsonPath("$.handoffPayload").exists());
    }
}
