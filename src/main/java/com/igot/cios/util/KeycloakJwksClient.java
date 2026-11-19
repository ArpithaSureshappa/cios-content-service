package com.igot.cios.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches the Platform's (Keycloak's) own JWKS for a given issuer, via its OIDC discovery
 * document - same discovery-then-jwks_uri approach as
 * {@link com.igot.cios.lti.service.LtiServiceImpl#checkPlatformOidcDiscovery}, but returning a
 * parsed Nimbus {@link JWKSet} for signature verification instead of a pass/fail diagnostic.
 * Used to verify bearer access tokens Keycloak itself issued (e.g. to a Tool via
 * client_credentials for AGS) - distinct from {@link Constants#KNOWN_TOOL_JWKS_URLS}, which
 * verifies a Tool-signed JWT in the opposite direction.
 */
@Slf4j
@Component
public class KeycloakJwksClient {

    private final RestTemplate restTemplate;

    @Autowired
    public KeycloakJwksClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public JWKSet fetchJwks(String issuer) throws Exception {
        String discoveryUrl = issuer + Constants.WELL_KNOWN_OPENID_CONFIGURATION_PATH;
        JsonNode discoveryDoc = restTemplate.getForObject(discoveryUrl, JsonNode.class);
        if (discoveryDoc == null || discoveryDoc.isMissingNode() || discoveryDoc.isNull()) {
            throw new IllegalStateException("Empty OIDC discovery response from " + discoveryUrl);
        }
        String jwksUri = discoveryDoc.path(Constants.JWKS_URI).asText("");
        if (StringUtils.isBlank(jwksUri)) {
            throw new IllegalStateException("No jwks_uri in OIDC discovery response from " + discoveryUrl);
        }
        String jwksJson = restTemplate.getForObject(jwksUri, String.class);
        return JWKSet.parse(jwksJson);
    }
}
