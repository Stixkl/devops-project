package com.circleguard.auth.client;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IdentityClient.class)
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
})
@TestPropertySource(properties = {
        "circleguard.client.identity-service.url=http://localhost:19083",
        "circleguard.client.identity-service.connect-timeout=2000",
        "circleguard.client.identity-service.read-timeout=3000",
        "circleguard.client.identity-service.write-timeout=3000",
        "resilience4j.circuitbreaker.instances.identityService.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.identityService.sliding-window-size=3",
        "resilience4j.circuitbreaker.instances.identityService.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.identityService.wait-duration-in-open-state=3s",
        "resilience4j.circuitbreaker.instances.identityService.permitted-number-of-calls-in-half-open-state=1",
        "resilience4j.circuitbreaker.instances.identityService.automatic-transition-from-open-to-half-open-enabled=true"
})
class IdentityClientIntegrationTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().port(19083));
        wireMock.start();
        configureFor("localhost", 19083);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Autowired
    private IdentityClient identityClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final IdentityMappingRequest REQUEST = new IdentityMappingRequest("test-user");

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("identityService").reset();
    }

    @Test
    void shouldReturnMappingInClosedState() {
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\"}")));

        var result = identityClient.mapIdentity(REQUEST);

        assertTrue(result.isPresent());
        assertEquals(
                UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                result.get().anonymousId()
        );
    }

    @Test
    void shouldReturnEmptyWhenCircuitIsOpen() {
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 2; i++) {
            identityClient.mapIdentity(REQUEST);
        }

        var cb = circuitBreakerRegistry.circuitBreaker("identityService");
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        var result = identityClient.mapIdentity(REQUEST);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldRecoverAfterHalfOpenTransition() throws Exception {
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 2; i++) {
            identityClient.mapIdentity(REQUEST);
        }

        var cb = circuitBreakerRegistry.circuitBreaker("identityService");
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        Thread.sleep(3500);

        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());

        wireMock.resetAll();
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\":\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\"}")));

        var result = identityClient.mapIdentity(REQUEST);
        assertTrue(result.isPresent());
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

}
