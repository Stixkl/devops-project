package com.circleguard.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private static final String SECRET = "my-super-secret-dev-key-32-chars-long-12345678";
    private JwtTokenService jwtTokenService;
    private Key parseKey;
    private final long expiration = 3600000;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, expiration);
        parseKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    @Test
    void generateToken_ShouldCreateValidJwtToken() {
        UUID anonymousId = UUID.randomUUID();
        List<GrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("PERMISSION_READ")
        );
        Authentication auth = new UsernamePasswordAuthenticationToken("test", "password", authorities);

        String token = jwtTokenService.generateToken(anonymousId, auth);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Verify token claims
        var parsed = Jwts.parserBuilder()
                .setSigningKey(parseKey)
                .build()
                .parseClaimsJws(token);

        assertEquals(anonymousId.toString(), parsed.getBody().getSubject());
        assertEquals(Arrays.asList("ROLE_USER", "PERMISSION_READ"), parsed.getBody().get("permissions"));
        assertNotNull(parsed.getBody().getIssuedAt());
        assertNotNull(parsed.getBody().getExpiration());
        assertTrue(parsed.getBody().getExpiration().after(new Date()));
    }

    @Test
    void generateToken_ShouldEncodePermissionsCorrectly() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user",
                "pass",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        String token = jwtTokenService.generateToken(anonymousId, auth);

        var parsed = Jwts.parserBuilder()
                .setSigningKey(parseKey)
                .build()
                .parseClaimsJws(token);

        List<String> permissions = (List<String>) parsed.getBody().get("permissions");
        assertEquals(1, permissions.size());
        assertEquals("ROLE_ADMIN", permissions.get(0));
    }
}