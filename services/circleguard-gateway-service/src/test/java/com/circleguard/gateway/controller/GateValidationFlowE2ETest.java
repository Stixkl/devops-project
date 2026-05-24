package com.circleguard.gateway.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.*;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GateValidationFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Value("${qr.secret}")
    private String qrSecret;

    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setup() {
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void validate_GreenStatusToken_AllowsEntry() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("GREEN");

        String token = buildQrToken(anonymousId, 60_000);
        ResponseEntity<Map> response = postValidate(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", true);
        assertThat(response.getBody()).containsEntry("status", "GREEN");
    }

    @Test
    void validate_ContagiedStatus_DeniesEntry() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("CONTAGIED");

        String token = buildQrToken(anonymousId, 60_000);
        ResponseEntity<Map> response = postValidate(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", false);
        assertThat(response.getBody()).containsEntry("status", "RED");
    }

    @Test
    void validate_PotentialStatus_DeniesEntry() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(valueOps.get("user:status:" + anonymousId)).thenReturn("POTENTIAL");

        String token = buildQrToken(anonymousId, 60_000);
        ResponseEntity<Map> response = postValidate(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", false);
        assertThat(response.getBody()).containsEntry("status", "RED");
    }

    @Test
    void validate_ExpiredToken_DeniesEntry() {
        String anonymousId = UUID.randomUUID().toString();
        String token = buildQrToken(anonymousId, -1_000);

        ResponseEntity<Map> response = postValidate(token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", false);
        assertThat(response.getBody()).containsEntry("message", "Invalid or Expired Token");
    }

    @Test
    void validate_MalformedToken_DeniesEntry() {
        ResponseEntity<Map> response = postValidate("not-a-jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("valid", false);
    }

    @Test
    void validate_MissingToken_Returns400() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/gate/validate",
                new HttpEntity<>(Map.of(), jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> postValidate(String token) {
        return restTemplate.postForEntity(
                "/api/v1/gate/validate",
                new HttpEntity<>(Map.of("token", token), jsonHeaders()),
                Map.class
        );
    }

    private String buildQrToken(String subject, long expiresInMs) {
        Key key = Keys.hmacShaKeyFor(qrSecret.getBytes());
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
