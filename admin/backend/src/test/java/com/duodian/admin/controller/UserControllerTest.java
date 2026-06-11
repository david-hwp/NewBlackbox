package com.duodian.admin.controller;

import com.duodian.admin.config.AuthContext;
import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.SubscriptionUpdateRequest;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.UserRepository;
import com.duodian.admin.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {
    private final UserService userService = mock(UserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserController controller = new UserController(userService, userRepository);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void normalUserCannotListAdminUsers() {
        AuthContext.setUserId(2L);
        User normalUser = user(2L, "USER");
        when(userService.findById(2L)).thenReturn(Optional.of(normalUser));

        ApiResponse<?> response = controller.list(null, null, null, null, null);

        assertThat(response.getCode()).isEqualTo(403);
        verify(userService, never()).findAll();
    }

    @Test
    void adminCanUpdateSubscription() {
        AuthContext.setUserId(1L);
        User admin = user(1L, "ADMIN");
        User updated = user(2L, "USER");
        updated.setSubscriptionPlan(UserService.PLAN_YEARLY);
        updated.setSubscriptionExpiresAt(LocalDateTime.now().plusYears(1));
        SubscriptionUpdateRequest request = new SubscriptionUpdateRequest();
        request.setPlan("YEARLY");
        when(userService.findById(1L)).thenReturn(Optional.of(admin));
        when(userService.updateSubscription(2L, "YEARLY")).thenReturn(updated);

        ApiResponse<User> response = controller.updateSubscription(2L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getSubscriptionPlan()).isEqualTo(UserService.PLAN_YEARLY);
        verify(userService).updateSubscription(2L, "YEARLY");
    }

    private User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setPhone("1380000000" + id);
        user.setRole(role);
        return user;
    }
}

