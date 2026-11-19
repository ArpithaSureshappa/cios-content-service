package com.igot.cios.lti.ags.controller;

import com.igot.cios.lti.ags.dto.LineItemDto;
import com.igot.cios.lti.ags.dto.ResultDto;
import com.igot.cios.lti.ags.dto.ScoreDto;
import com.igot.cios.lti.ags.exception.AgsApiException;
import com.igot.cios.lti.ags.security.AgsBearerTokenValidator;
import com.igot.cios.lti.ags.service.AgsService;
import com.igot.cios.util.Constants;
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
@RequestMapping("/ciosIntegration/lti/ags/{partnerCode}")
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

    @GetMapping("/lineitems")
    public ResponseEntity<List<LineItemDto>> listLineItems(@PathVariable String partnerCode,
                                                             @RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization,
                                                             @RequestParam(value = "resource_link_id", required = false) String resourceLinkId,
                                                             @RequestParam(value = "resource_id", required = false) String resourceId,
                                                             @RequestParam(value = "tag", required = false) String tag,
                                                             @RequestParam(value = "limit", required = false, defaultValue = "" + Constants.DEFAULT_AGS_PAGE_LIMIT) int limit,
                                                             @RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        bearerTokenValidator.validate(authorization, partnerCode, Constants.AGS_SCOPE_LINEITEM, Constants.AGS_SCOPE_LINEITEM_READONLY);
        Page<LineItemDto> result = agsService.listLineItems(partnerCode, resourceLinkId, resourceId, tag, limit, page);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(Constants.AGS_LINEITEMCONTAINER_CONTENT_TYPE));
        if (result.hasNext()) {
            String nextUrl = UriComponentsBuilder.fromUriString("/ciosIntegration/lti/ags/" + partnerCode + "/lineitems")
                    .queryParamIfPresent("resource_link_id", java.util.Optional.ofNullable(resourceLinkId))
                    .queryParamIfPresent("resource_id", java.util.Optional.ofNullable(resourceId))
                    .queryParamIfPresent("tag", java.util.Optional.ofNullable(tag))
                    .queryParam("limit", limit)
                    .queryParam("page", page + 1)
                    .build().encode().toUriString();
            response.header(Constants.LINK_HEADER, "<" + nextUrl + ">; rel=\"next\"");
        }
        return response.body(result.getContent());
    }

    @GetMapping("/lineitems/{lineItemId}")
    public ResponseEntity<LineItemDto> getLineItem(@PathVariable String partnerCode,
                                                     @PathVariable String lineItemId,
                                                     @RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization) {
        bearerTokenValidator.validate(authorization, partnerCode, Constants.AGS_SCOPE_LINEITEM, Constants.AGS_SCOPE_LINEITEM_READONLY);
        LineItemDto dto = agsService.getLineItem(partnerCode, lineItemId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(Constants.AGS_LINEITEM_CONTENT_TYPE)).body(dto);
    }

    @PutMapping("/lineitems/{lineItemId}")
    public ResponseEntity<LineItemDto> updateLineItem(@PathVariable String partnerCode,
                                                        @PathVariable String lineItemId,
                                                        @RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization,
                                                        @RequestBody LineItemDto lineItemDto) {
        bearerTokenValidator.validate(authorization, partnerCode, Constants.AGS_SCOPE_LINEITEM);
        LineItemDto updated = agsService.updateLineItem(partnerCode, lineItemId, lineItemDto);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(Constants.AGS_LINEITEM_CONTENT_TYPE)).body(updated);
    }

    @DeleteMapping("/lineitems/{lineItemId}")
    public ResponseEntity<Void> deleteLineItem(@PathVariable String partnerCode,
                                                 @PathVariable String lineItemId,
                                                 @RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization) {
        bearerTokenValidator.validate(authorization, partnerCode, Constants.AGS_SCOPE_LINEITEM);
        agsService.deleteLineItem(partnerCode, lineItemId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lineitems/{lineItemId}/scores")
    public ResponseEntity<Void> publishScore(@PathVariable String partnerCode,
                                               @PathVariable String lineItemId,
                                               @RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization,
                                               @RequestBody ScoreDto scoreDto) {
        bearerTokenValidator.validate(authorization, partnerCode, Constants.AGS_SCOPE_SCORE);
        agsService.publishScore(partnerCode, lineItemId, scoreDto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/lineitems/{lineItemId}/results")
    public ResponseEntity<List<ResultDto>> listResults(@PathVariable String partnerCode,
                                                          @PathVariable String lineItemId,
                                                          @RequestHeader(Constants.AUTHORIZATION_HEADER) String authorization,
                                                          @RequestParam(value = "user_id", required = false) String userId,
                                                          @RequestParam(value = "limit", required = false, defaultValue = "" + Constants.DEFAULT_AGS_PAGE_LIMIT) int limit,
                                                          @RequestParam(value = "page", required = false, defaultValue = "0") int page) {
        bearerTokenValidator.validate(authorization, partnerCode, Constants.AGS_SCOPE_RESULT_READONLY);
        Page<ResultDto> result = agsService.listResults(partnerCode, lineItemId, userId, limit, page);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(Constants.AGS_RESULTCONTAINER_CONTENT_TYPE));
        if (result.hasNext()) {
            String nextUrl = UriComponentsBuilder.fromUriString(
                            "/ciosIntegration/lti/ags/" + partnerCode + "/lineitems/" + lineItemId + "/results")
                    .queryParamIfPresent("user_id", java.util.Optional.ofNullable(userId))
                    .queryParam("limit", limit)
                    .queryParam("page", page + 1)
                    .build().encode().toUriString();
            response.header(Constants.LINK_HEADER, "<" + nextUrl + ">; rel=\"next\"");
        }
        return response.body(result.getContent());
    }

    @ExceptionHandler(AgsApiException.class)
    public ResponseEntity<Map<String, String>> handleAgsApiException(AgsApiException e) {
        log.error("AgsController: {} - {}", e.getStatus(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
}
