package com.circleguard.dashboard.controller;

import com.circleguard.dashboard.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    void shouldReturnHealthBoardStats() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalGreen", 1500);
        stats.put("totalExposed", 45);

        Mockito.when(analyticsService.getGlobalHealthStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/analytics/health-board")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGreen").value(1500))
                .andExpect(jsonPath("$.totalExposed").value(45));
    }

    @Test
    void shouldReturnTrendsForLocation() throws Exception {
        UUID locationId = UUID.randomUUID();
        List<Map<String, Object>> trends = new ArrayList<>();
        Map<String, Object> entry = new HashMap<>();
        entry.put("hour", "08:00");
        entry.put("count", 120);
        trends.add(entry);

        Mockito.when(analyticsService.getEntryTrends(locationId)).thenReturn(trends);

        mockMvc.perform(get("/api/v1/analytics/trends/" + locationId)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hour").value("08:00"))
                .andExpect(jsonPath("$[0].count").value(120));
    }

    @Test
    void shouldReturnCampusSummary() throws Exception {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalActive", 5000);
        summary.put("totalSuspect", 50);
        summary.put("totalProbable", 20);
        summary.put("totalConfirmed", 5);

        Mockito.when(analyticsService.getCampusSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/summary")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActive").value(5000))
                .andExpect(jsonPath("$.totalConfirmed").value(5));
    }

    @Test
    void shouldReturnDepartmentStats() throws Exception {
        Map<String, Object> deptStats = new HashMap<>();
        deptStats.put("department", "Engineering");
        deptStats.put("safeCount", 500);
        deptStats.put("riskCount", 2);

        Mockito.when(analyticsService.getDepartmentStats("Engineering")).thenReturn(deptStats);

        mockMvc.perform(get("/api/v1/analytics/department/Engineering")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("Engineering"));
    }

    @Test
    void shouldReturnTimeSeriesWithDefaultParams() throws Exception {
        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("bucket", new Date());
        point.put("status", "ACTIVE");
        point.put("total", 200);
        series.add(point);

        Mockito.when(analyticsService.getTimeSeries("hourly", 24)).thenReturn(series);

        mockMvc.perform(get("/api/v1/analytics/time-series")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void shouldReturnTimeSeriesWithCustomParams() throws Exception {
        List<Map<String, Object>> series = new ArrayList<>();
        Mockito.when(analyticsService.getTimeSeries("daily", 7)).thenReturn(series);

        mockMvc.perform(get("/api/v1/analytics/time-series?period=daily&limit=7")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser
    void shouldHandleInvalidLocationIdGracefully() throws Exception {
        UUID invalidId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        Mockito.when(analyticsService.getEntryTrends(invalidId)).thenThrow(new RuntimeException("Location not found"));

        mockMvc.perform(get("/api/v1/analytics/trends/" + invalidId))
                .andExpect(status().isInternalServerError());
    }
}
