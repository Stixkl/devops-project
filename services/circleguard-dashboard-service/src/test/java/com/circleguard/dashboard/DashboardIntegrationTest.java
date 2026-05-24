package com.circleguard.dashboard;

import com.circleguard.dashboard.client.PromotionClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class DashboardIntegrationTest {

    private static WireMockServer wireMockServer;
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
    void setUp() throws Exception {
        wireMockServer.resetAll();
        client = new PromotionClient();
        var field = PromotionClient.class.getDeclaredField("promotionServiceUrl");
        field.setAccessible(true);
        field.set(client, "http://localhost:8089");
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
    void getHealthStats_WhenPromotionServiceDown_ShouldReturnErrorMap() {
        wireMockServer.stubFor(get(urlEqualTo("/api/v1/health-status/stats"))
                .willReturn(aResponse().withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())));

        Map<String, Object> stats = client.getHealthStats();

        assertNotNull(stats);
        assertEquals("Service unavailable", stats.get("error"));
    }
}
