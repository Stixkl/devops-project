package com.circleguard.notification.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AuthServiceClient.class)
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class,
    MailSenderAutoConfiguration.class
})
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
