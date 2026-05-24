package com.circleguard.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.security.Key;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private static final String SECRET = "my-super-secret-dev-key-32-chars-long-12345678";
    private static final long EXPIRATION = 3600000L;

    private JwtTokenService jwtTokenService;
    private Key key;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, EXPIRATION);
        key = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    @Test
    @DisplayName("Should generate valid JWT token with correct claims")
    void generateToken_ShouldCreateValidToken() {
        UUID anonymousId = UUID.randomUUID();
        User principal = new User("testuser", "password",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        String token = jwtTokenService.generateToken(anonymousId, auth);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        var claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
        assertNotNull(claims.get("permissions"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("Should generate token that expires at correct time")
    void generateToken_ShouldHaveCorrectExpiration() {
        UUID anonymousId = UUID.randomUUID();
        User principal = new User("testuser", "password", Arrays.asList());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        String token = jwtTokenService.generateToken(anonymousId, auth);

        var claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        long expectedExpiration = claims.getIssuedAt().getTime() + EXPIRATION;
        assertEquals(expectedExpiration, claims.getExpiration().getTime(), 100);
    }

    @Test
    @DisplayName("Should generate different tokens for different users")
    void generateToken_ShouldBeUniquePerUser() {
        UUID anonymousId1 = UUID.randomUUID();
        UUID anonymousId2 = UUID.randomUUID();
        User principal = new User("testuser", "password", Arrays.asList());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        String token1 = jwtTokenService.generateToken(anonymousId1, auth);
        String token2 = jwtTokenService.generateToken(anonymousId2, auth);

        assertNotEquals(token1, token2);
    }
}