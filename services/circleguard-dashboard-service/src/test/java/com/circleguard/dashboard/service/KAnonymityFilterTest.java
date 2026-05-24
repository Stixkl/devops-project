package com.circleguard.dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KAnonymityFilterTest {

    private KAnonymityFilter kAnonymityFilter;

    @BeforeEach
    void setUp() {
        kAnonymityFilter = new KAnonymityFilter();
    }

    @Test
    void apply_ShouldRedactSmallCountsBelowThreshold() {
        // Given
        Map<String, Object> raw = new HashMap<>();
        raw.put("department", "Engineering");
        raw.put("studentCount", 3); // < 5
        raw.put("averageGPA", 3.5);

        // When
        Map<String, Object> result = kAnonymityFilter.apply(raw);

        // Then
        assertEquals("<5", result.get("studentCount"));
        assertTrue(result.containsKey("note"));
        assertEquals("Insufficient data for privacy", result.get("note"));
        assertEquals("Engineering", result.get("department"));
        assertEquals(3.5, result.get("averageGPA")); // other fields preserved
    }

    @Test
    void apply_ShouldKeepCountsAboveThreshold() {
        // Given
        Map<String, Object> raw = new HashMap<>();
        raw.put("department", "Medicine");
        raw.put("studentCount", 150); // >=5

        // When
        Map<String, Object> result = kAnonymityFilter.apply(raw);

        // Then
        assertEquals(150, result.get("studentCount"));
        assertFalse(result.containsKey("note"));
    }

    @Test
    void apply_ShouldHandleMultipleAnonymizableFields() {
        // Given
        Map<String, Object> raw = new HashMap<>();
        raw.put("suspectCount", 2);
        raw.put("probableCount", 8);
        raw.put("confirmedCount", 1);

        // When
        Map<String, Object> result = kAnonymityFilter.apply(raw);

        // Then
        assertEquals("<5", result.get("suspectCount"));
        assertEquals(8, result.get("probableCount")); // >=5 kept
        assertEquals("<5", result.get("confirmedCount"));
        assertTrue(result.containsKey("note"));
    }

    @Test
    void apply_ShouldHandleEmptyMap() {
        // Given
        Map<String, Object> raw = new HashMap<>();

        // When
        Map<String, Object> result = kAnonymityFilter.apply(raw);

        // Then
        assertTrue(result.isEmpty());
        assertFalse(result.containsKey("note"));
    }

    @Test
    void apply_ShouldNotModifyNonNumericValues() {
        // Given
        Map<String, Object> raw = new HashMap<>();
        raw.put("name", "Department A");
        raw.put("code", "ENG-001");
        raw.put("active", true);

        // When
        Map<String, Object> result = kAnonymityFilter.apply(raw);

        // Then
        assertEquals("Department A", result.get("name"));
        assertEquals("ENG-001", result.get("code"));
        assertEquals(true, result.get("active"));
        assertFalse(result.containsKey("note"));
    }
}