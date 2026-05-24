package com.circleguard.auth.controller;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalUserRepository localUserRepository;

    @BeforeEach
    void setUp() {
    }

    @Test
    @WithMockUser
    void shouldReturnUsersByPermission() throws Exception {
        LocalUser user1 = new LocalUser();
        user1.setUsername("admin1");
        user1.setEmail("admin1@example.com");

        LocalUser user2 = new LocalUser();
        user2.setUsername("admin2");
        user2.setEmail("admin2@example.com");

        List<LocalUser> users = Arrays.asList(user1, user2);

        when(localUserRepository.findUsersByPermissionName("ADMIN")).thenReturn(users);

        mockMvc.perform(get("/api/v1/users/permissions/ADMIN")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("admin1"))
                .andExpect(jsonPath("$[0].email").value("admin1@example.com"));
    }

    @Test
    @WithMockUser
    void shouldReturnEmptyListWhenNoUsersFound() throws Exception {
        when(localUserRepository.findUsersByPermissionName("UNKNOWN")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/permissions/UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}