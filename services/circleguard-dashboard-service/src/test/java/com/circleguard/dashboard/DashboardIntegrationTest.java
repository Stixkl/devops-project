package com.circleguard.dashboard;

import com.circleguard.dashboard.client.PromotionClient;
import com.circleguard.dashboard.config.DashboardProperties;
import com.circleguard.dashboard.config.RestClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = DashboardIntegrationTest.TestApp.class,
        properties = "circleguard.promotion-service.url=http://localhost:8089"
)
class DashboardIntegrationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @ComponentScan(basePackages = "com.circleguard.dashboard.client")
    @ConfigurationPropertiesScan(basePackages = "com.circleguard.dashboard.config")
    @Import(RestClientConfig.class)
    static class TestApp {
    }

    private static WireMockServer wireMockServer;

    @Autowired
    private PromotionClient client;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
    }

    @Test
    void getHealthStats_WhenPromotionServiceAvailable_ShouldReturnStats() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/health-status/stats"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"activeCount\": 5000, \"suspectCount\": 30, \"probableCount\": 15, \"confirmedCount\": 5}")));

        Map<String, Object> stats = client.getHealthStats();

        assertNotNull(stats);
        assertEquals(5000, stats.get("activeCount"));
        assertEquals(30, stats.get("suspectCount"));
        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/v1/health-status/stats")));
    }

    @Test
    void getHealthStatsByDepartment_WhenPromotionServiceReturnsData_ShouldParseDepartmentStats() {
        String department = "Engineering";
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/health-status/stats/department/" + department))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"department\":\"Engineering\",\"suspectCount\":2,\"confirmedCount\":0,\"safeCount\":450}")));

        Map<String, Object> deptStats = client.getHealthStatsByDepartment(department);

        assertNotNull(deptStats);
        assertEquals("Engineering", deptStats.get("department"));
        assertEquals(2, deptStats.get("suspectCount"));
        assertEquals(450, deptStats.get("safeCount"));
    }

    @Test
    void getHealthStats_WhenPromotionServiceDown_ShouldReturnErrorMapViaCircuitBreakerFallback() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/health-status/stats"))
                .willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())));

        Map<String, Object> stats = client.getHealthStats();

        assertNotNull(stats);
        assertEquals("Service unavailable", stats.get("error"));
    }
}
