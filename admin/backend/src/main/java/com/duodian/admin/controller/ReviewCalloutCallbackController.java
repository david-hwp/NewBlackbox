package com.duodian.admin.controller;

import com.duodian.admin.controller.dto.GookiCallbackResponse;
import com.duodian.admin.controller.dto.GookiCalloutCallbackRequest;
import com.duodian.admin.entity.ShopOrderReviewCalloutResult;
import com.duodian.admin.service.ReviewCalloutCallbackService;
import com.duodian.admin.service.ReviewCalloutCallbackTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/external/review-callouts/gooki")
public class ReviewCalloutCallbackController {
    private final ReviewCalloutCallbackTokenService tokenService;
    private final ReviewCalloutCallbackService callbackService;

    public ReviewCalloutCallbackController(
            ReviewCalloutCallbackTokenService tokenService,
            ReviewCalloutCallbackService callbackService
    ) {
        this.tokenService = tokenService;
        this.callbackService = callbackService;
    }

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GookiCallbackResponse> jsonCallback(
            @RequestHeader(value = ReviewCalloutCallbackTokenService.HEADER_NAME, required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestBody GookiCalloutCallbackRequest request
    ) {
        return handleCallback(headerToken, queryToken, request);
    }

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<GookiCallbackResponse> formCallback(
            @RequestHeader(value = ReviewCalloutCallbackTokenService.HEADER_NAME, required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @ModelAttribute GookiCalloutCallbackRequest request
    ) {
        return handleCallback(headerToken, queryToken, request);
    }

    private ResponseEntity<GookiCallbackResponse> handleCallback(
            String headerToken,
            String queryToken,
            GookiCalloutCallbackRequest request
    ) {
        if (!tokenService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(GookiCallbackResponse.error(503, "callback disabled"));
        }
        if (!tokenService.isValid(headerToken, queryToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(GookiCallbackResponse.error(401, "invalid token"));
        }
        ShopOrderReviewCalloutResult ignored = callbackService.handle(request);
        return ResponseEntity.ok(GookiCallbackResponse.ok());
    }
}
