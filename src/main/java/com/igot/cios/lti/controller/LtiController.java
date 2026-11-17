package com.igot.cios.lti.controller;

import com.igot.cios.dto.SBApiResponse;
import com.igot.cios.lti.service.LtiService;
import com.igot.cios.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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

    /**
     * AGS (Assignment and Grade Services) stub, public/unauthenticated like the other Tool-facing
     * LTI endpoints above - see {@link com.igot.cios.util.Constants#AGS_LINEITEM_BASE_URL} javadoc
     * for why this exists with no real grade persistence behind it. Response bodies/content-types
     * follow the IMS AGS spec shape directly (not the SBApiResponse envelope used elsewhere in this
     * controller), since the caller is a Tool's AGS client library, not iGOT's own frontend.
     */
    @GetMapping(value = "/ags/lineitems", produces = Constants.AGS_LINEITEMS_CONTAINER_CONTENT_TYPE)
    public ResponseEntity<List<Map<String, Object>>> getLineItems() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(Constants.AGS_LINEITEMS_CONTAINER_CONTENT_TYPE))
                .body(ltiService.getLineItems());
    }

    @GetMapping(value = "/ags/lineitems/{lineItemId}/lineitem", produces = Constants.AGS_LINEITEM_CONTENT_TYPE)
    public ResponseEntity<Map<String, Object>> getLineItem(@PathVariable String lineItemId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(Constants.AGS_LINEITEM_CONTENT_TYPE))
                .body(ltiService.getLineItem(lineItemId));
    }
}
