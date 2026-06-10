package com.circleguard.auth.controller;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.client.IdentityMapping;
import com.circleguard.auth.client.IdentityMappingRequest;
import com.circleguard.auth.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final AuthenticationManager authManager;
    private final JwtTokenService jwtService;
    private final IdentityClient identityClient;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            Optional<IdentityMapping> mapping = identityClient.mapIdentity(new IdentityMappingRequest(username));
            String mode;

            UUID anonymousId;
            if (mapping.isPresent()) {
                anonymousId = mapping.get().anonymousId();
                mode = "full";
            } else {
                log.warn("Login degraded: identity-service unavailable, proceeding without identity mapping");
                anonymousId = UUID.nameUUIDFromBytes(auth.getName().getBytes());
                mode = "degraded";

                Map<String, String> degradedBody = new LinkedHashMap<>();
                degradedBody.put("token", jwtService.generateToken(anonymousId, auth));
                degradedBody.put("type", "Bearer");
                degradedBody.put("anonymousId", anonymousId.toString());
                degradedBody.put("mode", mode);

                return ResponseEntity.ok()
                        .header("X-Auth-Degraded", "true")
                        .body(degradedBody);
            }

            String token = jwtService.generateToken(anonymousId, auth);

            Map<String, String> responseBody = new LinkedHashMap<>();
            responseBody.put("token", token);
            responseBody.put("type", "Bearer");
            responseBody.put("anonymousId", anonymousId.toString());
            responseBody.put("mode", mode);

            return ResponseEntity.ok(responseBody);
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        } catch (Exception e) {
            log.error("Unexpected error during login for {}", username, e);
            return ResponseEntity.status(500).body(Map.of("message", "Internal server error"));
        }
    }

    @PostMapping("/visitor/handoff")
    public ResponseEntity<Map<String, String>> generateVisitorHandoff(@RequestBody Map<String, String> request) {
        String anonymousIdStr = request.get("anonymousId");
        if (anonymousIdStr == null) {
            return ResponseEntity.badRequest().build();
        }

        UUID anonymousId = UUID.fromString(anonymousIdStr);

        Authentication visitorAuth = new UsernamePasswordAuthenticationToken(
                anonymousIdStr,
                null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("VISITOR"))
        );

        String token = jwtService.generateToken(anonymousId, visitorAuth);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "handoffPayload", "HANDOFF_TOKEN:" + anonymousId + ":" + token
        ));
    }
}
