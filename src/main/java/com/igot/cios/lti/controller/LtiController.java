package com.igot.cios.lti.controller;

import com.igot.cios.dto.SBApiResponse;
import com.igot.cios.lti.service.LtiService;
import com.igot.cios.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
}
