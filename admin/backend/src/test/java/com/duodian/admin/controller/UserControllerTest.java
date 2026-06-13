package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.ApiResponse;
import com.duodian.admin.controller.dto.SubscriptionUpdateRequest;
import com.duodian.admin.entity.User;
import com.duodian.admin.repository.UserRepository;
import com.duodian.admin.service.PermissionService;
import com.duodian.admin.service.UserService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {
    private final UserService userService = mock(UserService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final UserController controller = new UserController(userService, userRepository, permissionService);

    @Test
    void normalUserCannotListAdminUsers() {
        doThrow(new RuntimeException("无权限")).when(permissionService).requireAdminRole();

        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null))
                .hasMessage("无权限");
        verify(userService, never()).findAll();
    }

    @Test
    void adminCanUpdateSubscription() {
        User updated = user(2L, "USER");
        updated.setSubscriptionPlan(UserService.PLAN_YEARLY);
        updated.setSubscriptionExpiresAt(LocalDateTime.now().plusYears(1));
        SubscriptionUpdateRequest request = new SubscriptionUpdateRequest();
        request.setPlan("YEARLY");
        when(userService.updateSubscription(2L, "YEARLY")).thenReturn(updated);

        ApiResponse<User> response = controller.updateSubscription(2L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getSubscriptionPlan()).isEqualTo(UserService.PLAN_YEARLY);
        verify(permissionService).requireSuperAdmin();
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
