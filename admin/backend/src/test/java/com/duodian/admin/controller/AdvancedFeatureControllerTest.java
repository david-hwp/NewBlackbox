package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.AdvancedFeatureResponse;
import com.duodian.admin.controller.dto.AdvancedFeatureUpdateRequest;
import com.duodian.admin.service.AdvancedFeatureService;
import com.duodian.admin.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvancedFeatureControllerTest {
    private final AdvancedFeatureService advancedFeatureService = mock(AdvancedFeatureService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AdvancedFeatureController controller = new AdvancedFeatureController(
            advancedFeatureService,
            permissionService
    );

    @Test
    void adminListRequiresSuperAdmin() {
        when(advancedFeatureService.findAll()).thenReturn(List.of(response("bad_review_location")));

        var response = controller.list();

        assertThat(response.getCode()).isEqualTo(200);
        verify(permissionService).requireSuperAdmin();
    }

    @Test
    void appListDoesNotRequireSuperAdmin() {
        when(advancedFeatureService.findAll()).thenReturn(List.of(response("business_report")));

        var response = controller.appList();

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        verify(permissionService, never()).requireSuperAdmin();
    }

    @Test
    void updateRequiresSuperAdmin() {
        AdvancedFeatureUpdateRequest request = new AdvancedFeatureUpdateRequest();
        AdvancedFeatureResponse saved = response("outbound_praise");
        when(advancedFeatureService.update("outbound_praise", request)).thenReturn(saved);

        var response = controller.update("outbound_praise", request);

        assertThat(response.getData()).isSameAs(saved);
        verify(permissionService).requireSuperAdmin();
    }

    private AdvancedFeatureResponse response(String code) {
        AdvancedFeatureResponse response = new AdvancedFeatureResponse();
        response.setCode(code);
        response.setTitle(code);
        return response;
    }
}
