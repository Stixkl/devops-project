package com.circleguard.promotion.controller;

import com.circleguard.promotion.security.SecurityConfig;
import com.circleguard.promotion.service.HealthStatusService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthStatusController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=my-super-secret-test-key-32-chars-long!!"
})
class HealthStatusLifecycleE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthStatusService statusService;

    private static final String JWT_SECRET = "my-super-secret-test-key-32-chars-long!!";

    @Test
    void reportStatus_WithHealthCenterRole_Returns200() throws Exception {
        String jwt = buildJwt(List.of("ROLE_HEALTH_CENTER"));
        String anonymousId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/health/report")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"" + anonymousId + "\", \"status\": \"SUSPECT\"}"))
                .andExpect(status().isOk());

        Mockito.verify(statusService).updateStatus(anonymousId, "SUSPECT", false);
    }

    @Test
    void confirmPositive_WithHealthCenterRole_Returns200() throws Exception {
        String jwt = buildJwt(List.of("ROLE_HEALTH_CENTER"));
        String anonymousId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/health/confirmed")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"" + anonymousId + "\"}"))
                .andExpect(status().isOk());

        Mockito.verify(statusService).updateStatus(anonymousId, "CONFIRMED");
    }

    @Test
    void recover_WithHealthCenterRole_Returns200() throws Exception {
        String jwt = buildJwt(List.of("ROLE_HEALTH_CENTER"));
        String anonymousId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/health/recovery/" + anonymousId)
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());

        Mockito.verify(statusService).promoteToRecovered(anonymousId);
    }

    @Test
    void reportStatus_WithoutToken_Returns403() throws Exception {
        mockMvc.perform(post("/api/v1/health/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"user-1\", \"status\": \"SUSPECT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportStatus_WithStudentRole_Returns403() throws Exception {
        String jwt = buildJwt(List.of("ROLE_STUDENT"));

        mockMvc.perform(post("/api/v1/health/report")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"user-1\", \"status\": \"SUSPECT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportStatus_WithExpiredToken_Returns403() throws Exception {
        String expiredJwt = buildExpiredJwt(List.of("ROLE_HEALTH_CENTER"));

        mockMvc.perform(post("/api/v1/health/report")
                        .header("Authorization", "Bearer " + expiredJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousId\": \"user-1\", \"status\": \"SUSPECT\"}"))
                .andExpect(status().isForbidden());
    }

    private String buildJwt(List<String> roles) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .claim("permissions", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String buildExpiredJwt(List<String> roles) {
        Key key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
        return Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .claim("permissions", roles)
                .setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
