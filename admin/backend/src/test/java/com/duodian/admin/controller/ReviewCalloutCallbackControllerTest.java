package com.duodian.admin.controller;

import com.duodian.admin.config.ReviewCalloutProperties;
import com.duodian.admin.controller.dto.GookiCallbackResponse;
import com.duodian.admin.controller.dto.GookiCalloutCallbackRequest;
import com.duodian.admin.entity.ShopOrderReviewCalloutResult;
import com.duodian.admin.service.ReviewCalloutCallbackService;
import com.duodian.admin.service.ReviewCalloutCallbackTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReviewCalloutCallbackControllerTest {
    private final ReviewCalloutProperties properties = new ReviewCalloutProperties();
    private final ReviewCalloutCallbackTokenService tokenService = new ReviewCalloutCallbackTokenService(properties);
    private final ReviewCalloutCallbackService callbackService = mock(ReviewCalloutCallbackService.class);
    private final ReviewCalloutCallbackController controller = new ReviewCalloutCallbackController(tokenService, callbackService);

    @Test
    void disabledCallbackReturns503AndDoesNotHandle() {
        GookiCalloutCallbackRequest request = new GookiCalloutCallbackRequest();

        ResponseEntity<GookiCallbackResponse> response = controller.jsonCallback("token", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        verify(callbackService, never()).handle(any());
    }

    @Test
    void invalidTokenReturns401() {
        properties.getCallback().setEnabled(true);
        properties.getCallback().setToken("secret-token");

        ResponseEntity<GookiCallbackResponse> response = controller.jsonCallback("wrong", null, new GookiCalloutCallbackRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(callbackService, never()).handle(any());
    }

    @Test
    void validHeaderTokenReturnsGookiOk() {
        properties.getCallback().setEnabled(true);
        properties.getCallback().setToken("secret-token");
        GookiCalloutCallbackRequest request = new GookiCalloutCallbackRequest();
        when(callbackService.handle(request)).thenReturn(new ShopOrderReviewCalloutResult());

        ResponseEntity<GookiCallbackResponse> response = controller.jsonCallback("secret-token", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getCode()).isEqualTo(200);
        assertThat(response.getBody().getMessage()).isEqualTo("OK");
        verify(callbackService).handle(request);
    }

    @Test
    void validQueryTokenIsAcceptedWhenEnabled() {
        properties.getCallback().setEnabled(true);
        properties.getCallback().setToken("secret-token");
        properties.getCallback().setAcceptQueryToken(true);

        ResponseEntity<GookiCallbackResponse> response = controller.jsonCallback(null, "secret-token", new GookiCalloutCallbackRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(callbackService).handle(any());
    }

    @Test
    void formPayloadIsAcceptedWithSameTokenRules() {
        properties.getCallback().setEnabled(true);
        properties.getCallback().setToken("secret-token");
        GookiCalloutCallbackRequest request = new GookiCalloutCallbackRequest();
        request.setCdrId("cdr-1");

        ResponseEntity<GookiCallbackResponse> response = controller.formCallback("secret-token", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(callbackService).handle(request);
    }
}
