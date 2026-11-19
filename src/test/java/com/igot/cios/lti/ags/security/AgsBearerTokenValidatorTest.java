package com.igot.cios.lti.ags.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cios.lti.ags.exception.AgsApiException;
import com.igot.cios.plugins.DataTransformUtility;
import com.igot.cios.util.Constants;
import com.igot.cios.util.KeycloakJwksClient;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgsBearerTokenValidatorTest {

    private static final String PARTNER_CODE = "COURSERA";
    private static final String TOOL_ISSUER = "https://portal.dev.karmayogibharat.net/auth/realms/sunbird";
    private static final String CLIENT_ID = "lti-coursera";
    private static final String KID = "test-key-1";

    @Mock
    private DataTransformUtility dataTransformUtility;

    @Mock
    private KeycloakJwksClient keycloakJwksClient;

    private AgsBearerTokenValidator validator;
    private ObjectMapper mapper;
    private RSAKey signingKey;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        validator = new AgsBearerTokenValidator(dataTransformUtility, keycloakJwksClient);
        signingKey = new RSAKeyGenerator(2048).keyID(KID).keyUse(KeyUse.SIGNATURE).generate();

        lenient().when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(partnerResult());
        lenient().when(keycloakJwksClient.fetchJwks(TOOL_ISSUER)).thenReturn(new JWKSet(signingKey.toPublicJWK()));
    }

    private JsonNode partnerResult() {
        ObjectNode ltiConfig = mapper.createObjectNode();
        ltiConfig.put(Constants.TOOL_ISSUER, TOOL_ISSUER);
        ltiConfig.put(Constants.CLIENT_ID, CLIENT_ID);
        ObjectNode data = mapper.createObjectNode();
        data.set(Constants.LTI_CONFIGURATION, ltiConfig);
        ObjectNode result = mapper.createObjectNode();
        result.set(Constants.DATA, data);
        return result;
    }

    private String signedToken(RSAKey key, String kid, Map<String, Object> claims) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
        JWSObject jwsObject = new JWSObject(header, new Payload(mapper.writeValueAsString(claims)));
        JWSSigner signer = new RSASSASigner(key);
        jwsObject.sign(signer);
        return jwsObject.serialize();
    }

    private Map<String, Object> validClaims() {
        return Map.of(
                "iss", TOOL_ISSUER,
                "azp", CLIENT_ID,
                "exp", System.currentTimeMillis() / 1000 + 3600,
                "scope", Constants.AGS_SCOPE_SCORE + " " + Constants.AGS_SCOPE_LINEITEM
        );
    }

    @Test
    void validate_missingAuthorizationHeader_throwsUnauthorized() {
        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate(null, PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void validate_malformedAuthorizationHeader_throwsUnauthorized() {
        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate("Basic abc123", PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void validate_partnerLookupThrows_throwsNotFound() {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(anyString())).thenThrow(new RuntimeException("boom"));

        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate("Bearer sometoken", PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void validate_malformedJwt_throwsUnauthorized() {
        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate("Bearer not-a-jwt", PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void validate_signatureFromUnknownKey_throwsUnauthorized() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID(KID).keyUse(KeyUse.SIGNATURE).generate();
        String token = signedToken(otherKey, KID, validClaims());

        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate("Bearer " + token, PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void validate_expiredToken_throwsUnauthorized() throws Exception {
        Map<String, Object> claims = Map.of(
                "iss", TOOL_ISSUER,
                "azp", CLIENT_ID,
                "exp", System.currentTimeMillis() / 1000 - 3600,
                "scope", Constants.AGS_SCOPE_SCORE
        );
        String token = signedToken(signingKey, KID, claims);

        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate("Bearer " + token, PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void validate_issuerMismatch_throwsUnauthorized() throws Exception {
        Map<String, Object> claims = Map.of(
                "iss", "https://wrong-issuer.example.com/auth/realms/other",
                "azp", CLIENT_ID,
                "exp", System.currentTimeMillis() / 1000 + 3600,
                "scope", Constants.AGS_SCOPE_SCORE
        );
        String token = signedToken(signingKey, KID, claims);

        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate("Bearer " + token, PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void validate_azpMismatch_throwsForbidden() throws Exception {
        Map<String, Object> claims = Map.of(
                "iss", TOOL_ISSUER,
                "azp", "some-other-client",
                "exp", System.currentTimeMillis() / 1000 + 3600,
                "scope", Constants.AGS_SCOPE_SCORE
        );
        String token = signedToken(signingKey, KID, claims);

        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate("Bearer " + token, PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void validate_missingRequiredScope_throwsForbidden() throws Exception {
        Map<String, Object> claims = Map.of(
                "iss", TOOL_ISSUER,
                "azp", CLIENT_ID,
                "exp", System.currentTimeMillis() / 1000 + 3600,
                "scope", Constants.AGS_SCOPE_LINEITEM_READONLY
        );
        String token = signedToken(signingKey, KID, claims);

        AgsApiException ex = assertThrows(AgsApiException.class,
                () -> validator.validate("Bearer " + token, PARTNER_CODE, Constants.AGS_SCOPE_SCORE));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    @Test
    void validate_validTokenWithRequiredScope_doesNotThrow() throws Exception {
        String token = signedToken(signingKey, KID, validClaims());

        assertDoesNotThrow(() -> validator.validate("Bearer " + token, PARTNER_CODE, Constants.AGS_SCOPE_SCORE));
    }

    @Test
    void validate_validTokenWithAnyOfAcceptableScopes_doesNotThrow() throws Exception {
        String token = signedToken(signingKey, KID, validClaims());

        assertDoesNotThrow(() -> validator.validate("Bearer " + token, PARTNER_CODE,
                Constants.AGS_SCOPE_LINEITEM_READONLY, Constants.AGS_SCOPE_LINEITEM));
    }
}
