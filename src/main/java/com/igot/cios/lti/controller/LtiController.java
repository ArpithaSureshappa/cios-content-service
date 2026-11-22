package com.igot.cios.lti.controller;

import com.igot.cios.dto.SBApiResponse;
import com.igot.cios.lti.service.LtiService;
import com.igot.cios.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ciosIntegration/lti")
@Slf4j
public class LtiController {

    private final LtiService ltiService;

    @Autowired
    public LtiController(LtiService ltiService) {
        this.ltiService = ltiService;
    }

    @GetMapping("/ssotest/{partnerCode}")
    public ResponseEntity<SBApiResponse> testOidcConnection(
            @PathVariable String partnerCode,
            @RequestParam(required = false) String loginHint,
            @RequestParam(required = false) String messageHint
            ) {
        SBApiResponse response = ltiService.testOidcConnection(partnerCode, loginHint, messageHint);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/deeplinking/initiate/{partnerCode}")
    public ResponseEntity<SBApiResponse> initiateDeepLinking(
            @PathVariable String partnerCode,
            @RequestHeader(value = Constants.X_AUTH_TOKEN, required = false) String token) {

        SBApiResponse response = ltiService.initiateDeepLinking(partnerCode, token);
        // The body carries the admin's user id inside deeplinkUrl, so it must never be cached.
        return ResponseEntity.status(response.getResponseCode())
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    /**
     * Receives a Tool's signed Deep Linking response, posted from the admin's browser (no iGOT session).
     * The partner code arrives as a request param on the return URL; the JWT in the form body.
     */
    @PostMapping(value = "/deeplink/return", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<SBApiResponse> handleDeepLinkingReturn(
            @RequestParam(value = Constants.DEEPLINK_JWT_PARAM, required = false) String jwt,
            @RequestParam(value = Constants.PARTNER_CODE, required = false) String partnerCode) {
        SBApiResponse response = ltiService.handleDeepLinkingReturn(jwt, partnerCode);
        return ResponseEntity.status(response.getResponseCode())
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    /**
     * Receives a Tool's LTI 1.3 Deep Linking Response (e.g. Moodle's or Coursera's content
     * picker posting back the admin's selection). Public, unauthenticated by design - external
     * Tools call this directly with no iGOT user session.
     */
    @PostMapping("/deeplink/return")
    public ResponseEntity<SBApiResponse> handleDeepLinkingReturn(@RequestParam("JWT") String jwt) {
        SBApiResponse response = ltiService.handleDeepLinkingReturn(jwt);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
