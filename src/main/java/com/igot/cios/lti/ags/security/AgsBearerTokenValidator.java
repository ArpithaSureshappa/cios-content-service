package com.igot.cios.lti.ags.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cios.lti.ags.exception.AgsApiException;
import com.igot.cios.plugins.DataTransformUtility;
import com.igot.cios.util.Constants;
import com.igot.cios.util.KeycloakJwksClient;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies that an incoming AGS request carries a Keycloak-issued bearer access token
 * (obtained by the Tool via client_credentials against our own token endpoint) that is valid,
 * unexpired, issued for the given partner's registered client, and carries the specific AGS
 * scope the called endpoint requires. Mirrors the hand-rolled JWT verification already used by
 * {@link com.igot.cios.lti.service.LtiServiceImpl#handleDeepLinkingReturn} - but verifies
 * against Keycloak's OWN JWKS (via {@link KeycloakJwksClient}) rather than a Tool's, since here
 * Keycloak is the token issuer, not the Tool.
 */
@Slf4j
@Component
public class AgsBearerTokenValidator {

    private final DataTransformUtility dataTransformUtility;
    private final KeycloakJwksClient keycloakJwksClient;

    @Autowired
    public AgsBearerTokenValidator(DataTransformUtility dataTransformUtility, KeycloakJwksClient keycloakJwksClient) {
        this.dataTransformUtility = dataTransformUtility;
        this.keycloakJwksClient = keycloakJwksClient;
    }

    public void validate(String authorizationHeader, String partnerCode, String... acceptableScopes) {
        String token = extractBearerToken(authorizationHeader);

        JsonNode ltiConfig = fetchLtiConfig(partnerCode);
        String toolIssuer = ltiConfig.path(Constants.TOOL_ISSUER).asText("");
        String clientId = ltiConfig.path(Constants.CLIENT_ID).asText("");
        if (StringUtils.isBlank(toolIssuer) || StringUtils.isBlank(clientId)) {
            throw new AgsApiException(HttpStatus.BAD_REQUEST, Constants.LTI_CONFIGURATION_NOT_FOUND + partnerCode);
        }

        JWSObject jwsObject;
        try {
            jwsObject = JWSObject.parse(token);
        } catch (Exception e) {
            throw new AgsApiException(HttpStatus.UNAUTHORIZED, Constants.AGS_TOKEN_MALFORMED);
        }

        JWK matchedKey = findSigningKey(toolIssuer, jwsObject.getHeader().getKeyID());
        verifySignature(jwsObject, matchedKey);

        JsonNode claims = parseClaims(jwsObject);

        long exp = claims.path(Constants.AGS_EXP_CLAIM).asLong(0);
        if (exp <= System.currentTimeMillis() / 1000) {
            throw new AgsApiException(HttpStatus.UNAUTHORIZED, Constants.AGS_TOKEN_EXPIRED);
        }

        String iss = claims.path(Constants.AGS_ISS_CLAIM).asText("");
        if (!toolIssuer.equals(iss)) {
            throw new AgsApiException(HttpStatus.UNAUTHORIZED, Constants.AGS_TOKEN_ISSUER_MISMATCH + partnerCode);
        }

        String azp = claims.path(Constants.AGS_AZP_CLAIM).asText(claims.path(Constants.AGS_AUD_CLAIM).asText(""));
        if (!clientId.equals(azp)) {
            throw new AgsApiException(HttpStatus.FORBIDDEN, Constants.AGS_TOKEN_NOT_AUTHORIZED_FOR_PARTNER + partnerCode);
        }

        List<String> grantedScopes = Arrays.asList(claims.path(Constants.AGS_SCOPE_CLAIM).asText("").split(" "));
        boolean hasAnyAcceptableScope = Arrays.stream(acceptableScopes).anyMatch(grantedScopes::contains);
        if (!hasAnyAcceptableScope) {
            throw new AgsApiException(HttpStatus.FORBIDDEN,
                    Constants.AGS_TOKEN_MISSING_SCOPE + String.join(" or ", acceptableScopes));
        }
    }

    private JsonNode fetchLtiConfig(String partnerCode) {
        try {
            JsonNode partnerResult = dataTransformUtility.fetchPartnerInfoUsingApi(partnerCode);
            return partnerResult.path(Constants.DATA).path(Constants.LTI_CONFIGURATION);
        } catch (Exception e) {
            log.error("AgsBearerTokenValidator::fetchLtiConfig: failed to fetch partner details for {}", partnerCode, e);
            throw new AgsApiException(HttpStatus.NOT_FOUND, Constants.PARTNER_NOT_FOUND + partnerCode);
        }
    }

    private JWK findSigningKey(String toolIssuer, String kid) {
        JWKSet jwkSet;
        try {
            jwkSet = keycloakJwksClient.fetchJwks(toolIssuer);
        } catch (Exception e) {
            log.error("AgsBearerTokenValidator::findSigningKey: failed to fetch Keycloak JWKS for issuer {}", toolIssuer, e);
            throw new AgsApiException(HttpStatus.SERVICE_UNAVAILABLE, Constants.AGS_TOKEN_VERIFICATION_UNAVAILABLE);
        }
        JWK matchedKey = jwkSet.getKeyByKeyId(kid);
        if (matchedKey == null) {
            throw new AgsApiException(HttpStatus.UNAUTHORIZED, Constants.AGS_TOKEN_SIGNING_KEY_NOT_FOUND + kid);
        }
        return matchedKey;
    }

    private void verifySignature(JWSObject jwsObject, JWK matchedKey) {
        try {
            JWSVerifier verifier = new RSASSAVerifier(((RSAKey) matchedKey).toRSAPublicKey());
            if (!jwsObject.verify(verifier)) {
                throw new AgsApiException(HttpStatus.UNAUTHORIZED, Constants.AGS_TOKEN_SIGNATURE_INVALID);
            }
        } catch (AgsApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AgsApiException(HttpStatus.UNAUTHORIZED, Constants.AGS_TOKEN_SIGNATURE_INVALID);
        }
    }

    private JsonNode parseClaims(JWSObject jwsObject) {
        try {
            return new ObjectMapper().readTree(jwsObject.getPayload().toString());
        } catch (Exception e) {
            throw new AgsApiException(HttpStatus.UNAUTHORIZED, Constants.AGS_TOKEN_MALFORMED);
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (StringUtils.isBlank(authorizationHeader) || !authorizationHeader.startsWith(Constants.BEARER_PREFIX)) {
            throw new AgsApiException(HttpStatus.UNAUTHORIZED, Constants.MISSING_AUTHORIZATION_HEADER);
        }
        return authorizationHeader.substring(Constants.BEARER_PREFIX.length()).trim();
    }
}
