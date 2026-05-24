package com.circleguard.auth.controller;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private LocalUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private LdapAuthenticationProvider ldapAuthenticationProvider;

    @MockBean
    private IdentityClient identityClient;

    private static final UUID ANON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setup() {
        userRepository.deleteAll();

        LocalUser user = LocalUser.builder()
                .username("testuser")
                .password(passwordEncoder.encode("password123"))
                .email("test@circleguard.edu")
                .isActive(true)
                .roles(Set.of())
                .build();
        userRepository.save(user);

        Mockito.when(ldapAuthenticationProvider.authenticate(Mockito.any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("LDAP unavailable"));

        Mockito.when(identityClient.getAnonymousId("testuser")).thenReturn(ANON_ID);
    }

    @Test
    void login_ValidCredentials_ReturnsJwtToken() {
        Map<String, String> request = Map.of("username", "testuser", "password", "password123");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(request, jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        assertThat(response.getBody()).containsEntry("type", "Bearer");
        assertThat(response.getBody()).containsEntry("anonymousId", ANON_ID.toString());
    }

    @Test
    void login_WrongPassword_Returns401() {
        Map<String, String> request = Map.of("username", "testuser", "password", "wrongpassword");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(request, jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_UnknownUser_Returns401() {
        Map<String, String> request = Map.of("username", "nobody", "password", "any");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                new HttpEntity<>(request, jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void visitorHandoff_ValidAnonymousId_ReturnsHandoffPayload() {
        Map<String, String> request = Map.of("anonymousId", ANON_ID.toString());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/auth/visitor/handoff",
                new HttpEntity<>(request, jsonHeaders()),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        assertThat(response.getBody()).containsKey("handoffPayload");
        assertThat(response.getBody().get("handoffPayload").toString()).startsWith("HANDOFF_TOKEN:");
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
