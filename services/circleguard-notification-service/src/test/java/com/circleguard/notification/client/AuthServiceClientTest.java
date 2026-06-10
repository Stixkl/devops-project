package com.circleguard.notification.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {AuthServiceClient.class, AuthServiceClientTest.TestConfig.class})
@TestPropertySource(properties = {
    "circleguard.client.auth-service.url=http://localhost:1180",
    "circleguard.client.auth-service.connect-timeout=100",
    "circleguard.client.auth-service.read-timeout=100",
    "resilience4j.circuitbreaker.instances.authServicePermissions.minimum-number-of-calls=1",
    "resilience4j.circuitbreaker.instances.authServicePermissions.sliding-window-size=1",
    "resilience4j.circuitbreaker.instances.authServicePermissions.wait-duration-in-open-state=1s"
})
class AuthServiceClientTest {

    @Autowired
    private AuthServiceClient authServiceClient;

    @Configuration
    @EnableCircuitBreaker
    @ImportAutoConfiguration(Resilience4JAutoConfiguration.class)
    static class TestConfig {
    }

    @Test
    void fallbackShouldReturnBroadcastAllAfterCircuitOpens() {
        try {
            authServiceClient.getUsersByPermission("alert:receive_priority");
        } catch (Exception ignored) {
        }

        List<String> result = authServiceClient.getUsersByPermission("alert:receive_priority");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BROADCAST_ALL", result.get(0));
    }
}
