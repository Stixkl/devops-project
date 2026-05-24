package com.circleguard.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QrTokenServiceTest {

    private static final String QR_SECRET = "my-qr-secret-key-for-dev-1234567890";
    private static final long DEFAULT_EXPIRATION = 60000L;

    private QrTokenService qrTokenService;
    private Key key;

    @BeforeEach
    void setUp() {
        qrTokenService = new QrTokenService(QR_SECRET, DEFAULT_EXPIRATION);
        key = Keys.hmacShaKeyFor(QR_SECRET.getBytes());
    }

    @Test
    @DisplayName("Should generate valid QR token with correct subject")
    void generateQrToken_ShouldCreateValidToken() {
        UUID anonymousId = UUID.randomUUID();

        String token = qrTokenService.generateQrToken(anonymousId);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        var claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
    }

    @Test
    @DisplayName("Should generate token with correct expiration time")
    void generateQrToken_ShouldExpireAtCorrectTime() {
        UUID anonymousId = UUID.randomUUID();

        String token = qrTokenService.generateQrToken(anonymousId);

        var claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        long expirationDelta = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(DEFAULT_EXPIRATION, expirationDelta, 100);
    }

    @Test
    @DisplayName("Should generate different tokens for different anonymous IDs")
    void generateQrToken_ShouldBeUniquePerAnonymousId() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        String token1 = qrTokenService.generateQrToken(id1);
        String token2 = qrTokenService.generateQrToken(id2);

        assertNotEquals(token1, token2);
    }
}