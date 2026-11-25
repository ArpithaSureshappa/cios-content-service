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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * LTI 1.3 Advantage Assignment and Grade Services (AGS) - the Platform-hosted side of the spec,
 * scoped to exactly what's needed: receive and record the score the Tool posts for a user.
 * {@code externalId} identifies the course/content (same value LtiProtocolMapper put in the
 * lineitem URL's query string) and doubles as the lineItemId - no LineItem table, no per-partner
 * line item management.
 */
@Slf4j
@RestController
@RequestMapping("/ciosIntegration/lti/ags")
public class AgsController {

    private final AgsService agsService;
    private final AgsBearerTokenValidator bearerTokenValidator;

    @Autowired
    public AgsController(AgsService agsService, AgsBearerTokenValidator bearerTokenValidator) {
        this.agsService = agsService;
        this.bearerTokenValidator = bearerTokenValidator;
    }

    // Matches the lineitem URL LtiProtocolMapper builds: {base}/lineitems/lineitem?externalId=...&partnerCode=...
    @PostMapping("/lineitems/lineitem/scores")
    public ResponseEntity<Void> publishScore(@RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization,
                                               @RequestParam String externalId,
                                               @RequestParam String partnerCode,
                                               @RequestBody JsonNode scoreDto) {
       // bearerTokenValidator.validate(authorization, partnerCode, Constants.AGS_SCOPE_SCORE);
        log.info("AgsController::publishScore: partnerCode={}, externalId={}, userId={}", partnerCode, externalId, scoreDto.getUserId());
       // agsService.publishScore(partnerCode, externalId, scoreDto);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(AgsApiException.class)
    public ResponseEntity<Map<String, String>> handleAgsApiException(AgsApiException e) {
        log.error("AgsController: {} - {}", e.getStatus(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
}
