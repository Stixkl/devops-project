package com.circleguard.auth.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IdentityClient.class)
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
@TestPropertySource(properties = {
    "circleguard.client.identity-service.url=http://localhost:18083",
    "circleguard.client.identity-service.connect-timeout=100",
    "circleguard.client.identity-service.read-timeout=100",
    "resilience4j.circuitbreaker.instances.identityService.minimum-number-of-calls=1",
    "resilience4j.circuitbreaker.instances.identityService.sliding-window-size=1",
    "resilience4j.circuitbreaker.instances.identityService.wait-duration-in-open-state=1s"
})
class IdentityClientTest {

    @Autowired
    private IdentityClient identityClient;

    @Test
    void fallbackShouldReturnEmptyAfterCircuitOpens() {
        var request = new IdentityMappingRequest("test-user");
        try {
            identityClient.mapIdentity(request);
        } catch (Exception ignored) {
        }

        Optional<IdentityMapping> result = identityClient.mapIdentity(request);
        assertTrue(result.isEmpty());
    }
}
