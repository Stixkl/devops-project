package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircleServiceTest {

    @Mock
    private CircleNodeRepository circleNodeRepository;

    @InjectMocks
    private CircleService circleService;

    @Test
    @DisplayName("Should find circles by location ID")
    void findByLocationId_ReturnsCircles() {
        String locationId = "building-A-floor-2";
        CircleNode circle = new CircleNode();
        circle.setId(UUID.randomUUID().toString());
        circle.setName("Team Alpha");
        circle.setLocationId(locationId);

        when(circleNodeRepository.findByLocationId(locationId)).thenReturn(List.of(circle));

        var result = circleService.findByLocationId(locationId);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(circleNodeRepository, times(1)).findByLocationId(locationId);
    }

    @Test
    @DisplayName("Should return empty list when no circles found")
    void findByLocationId_NoCircles_ReturnsEmptyList() {
        String locationId = "unknown-location";

        when(circleNodeRepository.findByLocationId(locationId)).thenReturn(List.of());

        var result = circleService.findByLocationId(locationId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should check if circle is active")
    void isActiveCircle_WithValidCircle_ReturnsTrue() {
        CircleNode circle = new CircleNode();
        circle.setId(UUID.randomUUID().toString());
        circle.setName("Active Circle");
        circle.setActive(true);

        when(circleNodeRepository.findById(circle.getId()))
                .thenReturn(Optional.of(circle));

        boolean result = circleService.isActiveCircle(circle.getId());

        assertTrue(result);
    }
}