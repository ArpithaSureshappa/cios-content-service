package com.igot.cios.lti.ags.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cios.lti.ags.dto.LineItemDto;
import com.igot.cios.lti.ags.dto.ResultDto;
import com.igot.cios.lti.ags.dto.ScoreDto;
import com.igot.cios.lti.ags.exception.AgsApiException;
import com.igot.cios.lti.ags.security.AgsBearerTokenValidator;
import com.igot.cios.lti.ags.service.AgsService;
import com.igot.cios.util.Constants;
import com.nimbusds.jose.JWSObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * LTI 1.3 Advantage Assignment and Grade Services (AGS) - the Platform-hosted side of the spec.
 * Coursera (and any other Tool) calls these directly, authenticating with a Keycloak-issued
 * bearer access token (see {@link AgsBearerTokenValidator}) rather than iGOT's own session/SSO.
 * Response bodies are the exact IMS-spec shapes, not {@link com.igot.cios.dto.SBApiResponse} -
 * Tools expect the literal Line Item/Score/Result JSON, not our internal envelope.
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

    @PostMapping("/lineitems")
    public ResponseEntity<LineItemDto> createLineItem(@PathVariable String partnerCode,
                                                        @RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization,
                                                        @RequestBody LineItemDto lineItemDto) {
        bearerTokenValidator.validate(authorization, partnerCode, Constants.AGS_SCOPE_LINEITEM);
        LineItemDto created = agsService.createLineItem(partnerCode, lineItemDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.parseMediaType(Constants.AGS_LINEITEM_CONTENT_TYPE))
                .body(created);
    }


    @PostMapping("/lineitems/{lineItemId}/lineitem")
    public ResponseEntity<Void> publishScore(@PathVariable String partnerCode,
                                               @PathVariable String lineItemId,
                                               @RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization,
                                               @RequestBody ScoreDto scoreDto) {
        try {
            log.info("AgsController::publishScore: partnerCode={}, lineItemId={}, payload={}",
                    partnerCode, lineItemId, new ObjectMapper().writeValueAsString(scoreDto));
        } catch (Exception e) {
            log.error("AgsController::publishScore: failed to serialize payload for logging", e);
        }

        String jwtToken = (authorization != null && authorization.startsWith(Constants.BEARER_PREFIX))
                ? authorization.substring(Constants.BEARER_PREFIX.length()).trim()
                : authorization;
        log.info("AgsController::publishScore: jwtToken={}", jwtToken);

        try {
            JWSObject jwsObject = JWSObject.parse(jwtToken);
            log.info("AgsController::publishScore: decoded JWT header={}, claims={}",
                    jwsObject.getHeader().toString(), jwsObject.getPayload().toString());
        } catch (Exception e) {
            log.error("AgsController::publishScore: failed to decode JWT, continuing anyway", e);
        }

        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(AgsApiException.class)
    public ResponseEntity<Map<String, String>> handleAgsApiException(AgsApiException e) {
        log.error("AgsController: {} - {}", e.getStatus(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
}
