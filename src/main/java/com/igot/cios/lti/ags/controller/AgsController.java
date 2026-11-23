package com.igot.cios.lti.ags.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cios.lti.ags.dto.ScoreDto;
import com.igot.cios.lti.ags.exception.AgsApiException;
import com.igot.cios.lti.ags.security.AgsBearerTokenValidator;
import com.igot.cios.lti.ags.service.AgsService;
import com.igot.cios.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LTI 1.3 Advantage Assignment and Grade Services (AGS) - the Platform-hosted side of the spec,
 * scoped to exactly what's needed: receive and record the score Moodle (the one LTI 1.3 Tool
 * integrated today) posts for a user. No LineItem table, no per-partner line item management -
 * {@code lineItemId} is an opaque identifier from the URL, not a row we look up or validate.
 */
@Slf4j
@RestController
@RequestMapping("/ciosIntegration/lti/ags")
public class AgsController {

    // Keycloak's LtiProtocolMapper hardcodes this same partner code into the AGS claim it
    // issues, since Moodle is the only LTI 1.3 tool registered against this deployment today.
    private static final String PARTNER_CODE = "IGOT-MOODLE-W8HF4";

    private final AgsService agsService;
    private final AgsBearerTokenValidator bearerTokenValidator;

    @Autowired
    public AgsController(AgsService agsService, AgsBearerTokenValidator bearerTokenValidator) {
        this.agsService = agsService;
        this.bearerTokenValidator = bearerTokenValidator;
    }

    @PostMapping("/lineitems/1/lineitem/scores")
    public ResponseEntity<Void> publishScore(@RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization,
                                               @RequestBody JsonNode scoreDto) {
        bearerTokenValidator.validate(authorization, PARTNER_CODE, Constants.AGS_SCOPE_SCORE);
        log.info("AgsController: publishScore called with scoreDto: {}", scoreDto);
       // agsService.publishScore(PARTNER_CODE, "1", scoreDto);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(AgsApiException.class)
    public ResponseEntity<Map<String, String>> handleAgsApiException(AgsApiException e) {
        log.error("AgsController: {} - {}", e.getStatus(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
}
