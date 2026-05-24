package com.circleguard.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QrValidationServiceTest {

    private static final String QR_SECRET = "test-secret-key-for-unit-tests-32chars!!";

    private QrValidationService qrValidationService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() throws Exception {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        qrValidationService = new QrValidationService(redisTemplate);
        var secretField = QrValidationService.class.getDeclaredField("qrSecret");
        secretField.setAccessible(true);
        secretField.set(qrValidationService, QR_SECRET);
    }

    @Test
    void validateToken_WhenTokenValidAndStatusGreen_ShouldReturnGreen() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        String validJwt = createValidJwt(anonymousId);
        when(valueOps.get("user:status:" + anonymousId)).thenReturn("GREEN");

        var result = qrValidationService.validateToken(validJwt);

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
        assertEquals("Welcome to Campus", result.message());
    }

    @Test
    void validateToken_WhenUserStatusContagied_ShouldReturnRed() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        String validJwt = createValidJwt(anonymousId);
        when(valueOps.get("user:status:" + anonymousId)).thenReturn("CONTAGIED");

        var result = qrValidationService.validateToken(validJwt);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertTrue(result.message().contains("Health Risk"));
    }

    @Test
    void validateToken_WhenUserStatusPotential_ShouldReturnRed() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        String validJwt = createValidJwt(anonymousId);
        when(valueOps.get("user:status:" + anonymousId)).thenReturn("POTENTIAL");

        var result = qrValidationService.validateToken(validJwt);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void validateToken_WhenTokenMalformed_ShouldReturnRed() throws Exception {
        var result = qrValidationService.validateToken("malformed-token");

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    @Test
    void validateToken_WhenTokenExpired_ShouldReturnRed() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        String expiredJwt = createExpiredJwt(anonymousId);

        var result = qrValidationService.validateToken(expiredJwt);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Invalid or Expired Token", result.message());
    }

    private String createValidJwt(String subject) {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(QR_SECRET.getBytes());
        return io.jsonwebtoken.Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 60000))
                .signWith(key, io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }

    private String createExpiredJwt(String subject) {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(QR_SECRET.getBytes());
        return io.jsonwebtoken.Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new java.util.Date(System.currentTimeMillis() - 120000))
                .setExpiration(new java.util.Date(System.currentTimeMillis() - 60000))
                .signWith(key, io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }
}
