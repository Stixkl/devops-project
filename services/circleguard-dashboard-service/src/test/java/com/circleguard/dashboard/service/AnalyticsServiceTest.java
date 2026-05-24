package com.circleguard.dashboard.service;

import com.circleguard.dashboard.client.PromotionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

    private AnalyticsService analyticsService;
    private PromotionClient promotionClient;
    private JdbcTemplate jdbcTemplate;
    private KAnonymityFilter kAnonymityFilter;

    @BeforeEach
    void setUp() {
        promotionClient = Mockito.mock(PromotionClient.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        kAnonymityFilter = new KAnonymityFilter();
        analyticsService = new AnalyticsService(jdbcTemplate, promotionClient, kAnonymityFilter);
    }

    @Test
    void getCampusSummary_ShouldReturnPromotionClientStats() {
        // Given
        Map<String, Object> expected = Map.of(
                "totalActive", 5000,
                "totalSuspect", 30,
                "totalProbable", 15,
                "totalConfirmed", 3
        );
        when(promotionClient.getHealthStats()).thenReturn(expected);

        // When
        Map<String, Object> result = analyticsService.getCampusSummary();

        // Then
        assertEquals(5000, result.get("totalActive"));
        verify(promotionClient).getHealthStats();
    }

    @Test
    void getDepartmentStats_ShouldApplyKAnonymity() {
        // Given
        Map<String, Object> rawDeptStats = new HashMap<>();
        rawDeptStats.put("department", "CS");
        rawDeptStats.put("suspectCount", 2); // Will be anonymized
        rawDeptStats.put("confirmedCount", 0);
        rawDeptStats.put("safeCount", 50);
        when(promotionClient.getHealthStatsByDepartment("CS")).thenReturn(rawDeptStats);

        // When
        Map<String, Object> result = analyticsService.getDepartmentStats("CS");

        // Then
        assertEquals("<5", result.get("suspectCount"));
        assertTrue(((String) result.get("note")).contains("privacy"));
        assertEquals(50, result.get("safeCount")); // not anonymized
    }

    @Test
    void getGlobalHealthStats_ShouldReturnCampusSummary() {
        // Given
        Map<String, Object> summary = Map.of("totalActive", 10000, "totalExposed", 100);
        when(promotionClient.getHealthStats()).thenReturn(summary);

        // When
        Map<String, Object> result = analyticsService.getGlobalHealthStats();

        // Then
        assertEquals(10000, result.get("totalActive"));
    }

    @Test
    void getTimeSeries_ShouldReturnMockDataWhenTableMissing() {
        // Given
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenThrow(new RuntimeException("Table does not exist"));

        // When
        List<Map<String, Object>> result = analyticsService.getTimeSeries("hourly", 24);

        // Then
        assertFalse(result.isEmpty());
        assertTrue(result.size() <= 96); // 24 * 4 statuses max
    }

    @Test
    void getTimeSeries_ShouldQueryDatabaseForDailyPeriod() {
        // Given
        List<Map<String, Object>> dbResult = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bucket", new Date());
        row.put("status", "ACTIVE");
        row.put("total", 150);
        dbResult.add(row);
        when(jdbcTemplate.queryForList(anyString(), eq(7))).thenReturn(dbResult);

        // When
        List<Map<String, Object>> result = analyticsService.getTimeSeries("daily", 7);

        // Then
        verify(jdbcTemplate).queryForList(contains("date_trunc('day'"), eq(7));
        assertEquals(1, result.size());
    }
}