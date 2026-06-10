package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircleServiceTest {

    @Mock
    private CircleNodeRepository circleNodeRepository;

    @Mock
    private HealthStatusService healthStatusService;

    @InjectMocks
    private CircleService circleService;

    @Test
    @DisplayName("Should toggle circle validity")
    void toggleCircleValidity_FlipsIsValid() {
        Long circleId = 1L;
        CircleNode circle = CircleNode.builder()
                .id(circleId)
                .name("Test Circle")
                .isValid(true)
                .members(new HashSet<>())
                .build();

        when(circleNodeRepository.findById(circleId)).thenReturn(Optional.of(circle));
        when(circleNodeRepository.save(any())).thenReturn(circle);

        circleService.toggleCircleValidity(circleId);

        assertFalse(circle.getIsValid());
        verify(circleNodeRepository).save(circle);
    }

    @Test
    @DisplayName("Should create a new circle")
    void createCircle_ReturnsSavedCircle() {
        String name = "Team Alpha";
        String locationId = "building-A-floor-2";

        when(circleNodeRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(circleNodeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CircleNode result = circleService.createCircle(name, locationId);

        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(locationId, result.getLocationId());
        assertTrue(result.getIsActive());
        assertTrue(result.getInviteCode().startsWith("MESH-"));
        verify(circleNodeRepository).save(any());
    }
}