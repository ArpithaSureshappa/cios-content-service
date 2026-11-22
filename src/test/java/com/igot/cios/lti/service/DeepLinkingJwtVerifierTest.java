package com.igot.cios.lti.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cios.util.Constants;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DeepLinkingJwtVerifierTest {

    private static final String PARTNER_CODE = "IGOT-COURSERA-ABCDE";
    private static final String CLIENT_ID = "portal";
    private static final String PLATFORM_ISSUER = "https://portal.example.org/auth/realms/sunbird";
    private static final String JWKS_URL = "https://tool.example.com/.well-known/jwks.json";
    private static final String KID = "tool-key-1";

    private static RSAKey toolKey;
    private static RSAKey otherKey;

    private DeepLinkingJwtVerifier verifier;

    @BeforeAll
    static void generateKeys() throws Exception {
        toolKey = new RSAKeyGenerator(2048).keyID(KID).generate();
        otherKey = new RSAKeyGenerator(2048).keyID(KID).generate();
    }

    @BeforeEach
    void setUp() {
        verifier = spy(new DeepLinkingJwtVerifier());
        doReturn(new JWKSet(toolKey.toPublicJWK()).toString()).when(verifier).fetchJwks(JWKS_URL);
    }

    // ---------- helpers ----------

    private Map<String, Object> validItem() {
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("link_type", "COURSE");
        custom.put("course_id", "daG-a-O1EeijKBISCWxf6g");
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "ltiResourceLink");
        item.put("title", "AI For Everyone");
        item.put("text", "A course");
        item.put("url", "https://www.coursera.org/api/rest/v1/tool-lti/launch");
        item.put("custom", custom);
        return item;
    }

    private JWTClaimsSet.Builder validClaims() {
        long now = System.currentTimeMillis();
        return new JWTClaimsSet.Builder()
                .issuer(CLIENT_ID)
                .audience(PLATFORM_ISSUER)
                .issueTime(new Date(now - 5_000))
                .expirationTime(new Date(now + 540_000))
                .claim("nonce", "n-0S6_WzA2Mj")
                .claim(Constants.DEEPLINK_MESSAGE_TYPE_CLAIM, Constants.DEEPLINK_MESSAGE_TYPE_RESPONSE)
                .claim(Constants.DEEPLINK_VERSION_CLAIM, "1.3.0")
                .claim(Constants.DEEPLINK_DEPLOYMENT_ID_CLAIM, PARTNER_CODE)
                .claim(Constants.DEEPLINK_DATA_CLAIM, "Deeplink:" + PARTNER_CODE)
                .claim(Constants.DEEPLINK_CONTENT_ITEMS_CLAIM, List.of(validItem()));
    }

    private String sign(JWTClaimsSet claims, RSAKey key, String kid) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private String signed(Consumer<JWTClaimsSet.Builder> tweak) throws Exception {
        JWTClaimsSet.Builder builder = validClaims();
        tweak.accept(builder);
        return sign(builder.build(), toolKey, KID);
    }

    private JsonNode verifyJwt(String jwt) {
        return verifier.verifyAndExtractItems(jwt, PARTNER_CODE, CLIENT_ID, PARTNER_CODE, PLATFORM_ISSUER, JWKS_URL);
    }

    private LtiReturnException rejected(String jwt) {
        return assertThrows(LtiReturnException.class, () -> verifyJwt(jwt));
    }

    private void assertRejected(Consumer<JWTClaimsSet.Builder> tweak, HttpStatus status, String message) throws Exception {
        LtiReturnException e = rejected(signed(tweak));
        assertEquals(status, e.getStatus());
        assertEquals(message, e.getMessage());
    }

    // ---------- positive ----------

    @Test
    void validResponse_returnsTheSelectedItem() throws Exception {
        JsonNode items = verifyJwt(signed(b -> { }));

        assertEquals(1, items.size());
        assertEquals("AI For Everyone", items.get(0).path("title").asText());
        assertEquals("daG-a-O1EeijKBISCWxf6g", items.get(0).path("custom").path("course_id").asText());
    }

    @Test
    void audienceGivenAsAnArray_isAccepted() throws Exception {
        assertEquals(1, verifyJwt(signed(b -> b.audience(List.of(PLATFORM_ISSUER, "other")))).size());
    }

    @Test
    void keysAreCached_secondVerificationDoesNotDownloadAgain() throws Exception {
        verifyJwt(signed(b -> { }));
        verifyJwt(signed(b -> { }));

        verify(verifier, times(1)).fetchJwks(JWKS_URL);
    }

    // ---------- request / token shape: 400 ----------

    @Test
    void malformedToken_isBadRequest() {
        LtiReturnException e = rejected("not-a-jwt");
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals(Constants.DEEPLINK_JWT_MALFORMED, e.getMessage());
    }

    @Test
    void nonRs256Token_isBadRequest() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KID).build(), validClaims().build());
        jwt.sign(new MACSigner(new byte[64]));

        LtiReturnException e = rejected(jwt.serialize());

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals(Constants.DEEPLINK_JWT_UNSUPPORTED_ALG, e.getMessage());
    }

    @Test
    void tokenWithoutKid_isBadRequest() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), validClaims().build());
        jwt.sign(new RSASSASigner(toolKey));

        LtiReturnException e = rejected(jwt.serialize());

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals(Constants.DEEPLINK_JWT_MISSING_KID, e.getMessage());
    }

    @Test
    void unknownKid_isBadRequest() throws Exception {
        LtiReturnException e = rejected(sign(validClaims().build(), toolKey, "some-other-kid"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals(Constants.DEEPLINK_JWT_KID_NOT_FOUND, e.getMessage());
    }

    // ---------- signature: 401 ----------

    @Test
    void signedWithAnotherKey_isUnauthorized() throws Exception {
        LtiReturnException e = rejected(sign(validClaims().build(), otherKey, KID));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
        assertEquals(Constants.DEEPLINK_JWT_SIGNATURE_INVALID, e.getMessage());
    }

    @Test
    void tamperedPayload_isUnauthorized() throws Exception {
        String[] parts = signed(b -> { }).split("\\.");
        String forged = signed(b -> b.claim(Constants.DEEPLINK_DATA_CLAIM, "Deeplink:OTHER")).split("\\.")[1];

        LtiReturnException e = rejected(parts[0] + "." + forged + "." + parts[2]);

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatus());
    }

    // ---------- claims: 403 ----------

    @Test
    void wrongIssuer_isForbidden() throws Exception {
        assertRejected(b -> b.issuer("someone-else"), HttpStatus.FORBIDDEN, Constants.DEEPLINK_CLAIM_ISSUER_MISMATCH);
    }

    @Test
    void wrongAudience_isForbidden() throws Exception {
        assertRejected(b -> b.audience("https://other.example.org"), HttpStatus.FORBIDDEN,
                Constants.DEEPLINK_CLAIM_AUDIENCE_MISMATCH);
    }

    @Test
    void wrongDeploymentId_isForbidden() throws Exception {
        assertRejected(b -> b.claim(Constants.DEEPLINK_DEPLOYMENT_ID_CLAIM, "1"), HttpStatus.FORBIDDEN,
                Constants.DEEPLINK_CLAIM_DEPLOYMENT_MISMATCH);
    }

    @Test
    void wrongMessageType_isForbidden() throws Exception {
        assertRejected(b -> b.claim(Constants.DEEPLINK_MESSAGE_TYPE_CLAIM, "LtiResourceLinkRequest"), HttpStatus.FORBIDDEN,
                Constants.DEEPLINK_CLAIM_MESSAGE_TYPE_INVALID);
    }

    @Test
    void wrongVersion_isForbidden() throws Exception {
        assertRejected(b -> b.claim(Constants.DEEPLINK_VERSION_CLAIM, "1.1.0"), HttpStatus.FORBIDDEN,
                Constants.DEEPLINK_CLAIM_VERSION_INVALID);
    }

    @Test
    void expiredToken_isForbidden() throws Exception {
        assertRejected(b -> b.expirationTime(new Date(System.currentTimeMillis() - 120_000)), HttpStatus.FORBIDDEN,
                Constants.DEEPLINK_CLAIM_EXPIRED);
    }

    @Test
    void tokenWithoutExpiry_isForbidden() throws Exception {
        assertRejected(b -> b.expirationTime(null), HttpStatus.FORBIDDEN, Constants.DEEPLINK_CLAIM_EXPIRED);
    }

    @Test
    void tokenIssuedInTheFuture_isForbidden() throws Exception {
        assertRejected(b -> b.issueTime(new Date(System.currentTimeMillis() + 600_000)), HttpStatus.FORBIDDEN,
                Constants.DEEPLINK_CLAIM_ISSUED_IN_FUTURE);
    }

    @Test
    void missingNonce_isForbidden() throws Exception {
        assertRejected(b -> b.claim("nonce", null), HttpStatus.FORBIDDEN, Constants.DEEPLINK_CLAIM_NONCE_MISSING);
    }

    @Test
    void dataForAnotherPartner_isForbidden() throws Exception {
        assertRejected(b -> b.claim(Constants.DEEPLINK_DATA_CLAIM, "Deeplink:SOME-OTHER-PARTNER"), HttpStatus.FORBIDDEN,
                Constants.DEEPLINK_CLAIM_DATA_MISMATCH);
    }

    @Test
    void dataWithoutThePrefix_isForbidden() throws Exception {
        assertRejected(b -> b.claim(Constants.DEEPLINK_DATA_CLAIM, PARTNER_CODE), HttpStatus.FORBIDDEN,
                Constants.DEEPLINK_CLAIM_DATA_MISMATCH);
    }

    // ---------- content items: 400 ----------

    private void assertItemsRejected(Object itemsClaim) throws Exception {
        assertRejected(b -> b.claim(Constants.DEEPLINK_CONTENT_ITEMS_CLAIM, itemsClaim), HttpStatus.BAD_REQUEST,
                Constants.DEEPLINK_CONTENT_ITEMS_INVALID);
    }

    @Test
    void noItems_isBadRequest() throws Exception {
        assertItemsRejected(List.of());
    }

    @Test
    void twoItems_isBadRequest() throws Exception {
        assertItemsRejected(List.of(validItem(), validItem()));
    }

    @Test
    void itemsClaimNotAList_isBadRequest() throws Exception {
        assertItemsRejected("oops");
    }

    @Test
    void itemOfAnotherType_isBadRequest() throws Exception {
        Map<String, Object> item = validItem();
        item.put("type", "link");
        assertItemsRejected(List.of(item));
    }

    @Test
    void itemWithoutTitle_isBadRequest() throws Exception {
        Map<String, Object> item = validItem();
        item.remove("title");
        assertItemsRejected(List.of(item));
    }

    @Test
    void itemWithPlainHttpUrl_isBadRequest() throws Exception {
        Map<String, Object> item = validItem();
        item.put("url", "http://www.coursera.org/launch");
        assertItemsRejected(List.of(item));
    }

    @Test
    void itemWithoutCustom_isBadRequest() throws Exception {
        Map<String, Object> item = validItem();
        item.remove("custom");
        assertItemsRejected(List.of(item));
    }

    // ---------- key download ----------

    @Test
    void keyDownloadFromALoopbackHost_isRefused() {
        LtiReturnException e = assertThrows(LtiReturnException.class,
                () -> new DeepLinkingJwtVerifier().fetchJwks("https://127.0.0.1/jwks.json"));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
    }

    @Test
    void keyDownloadOverPlainHttp_isRefused() {
        LtiReturnException e = assertThrows(LtiReturnException.class,
                () -> new DeepLinkingJwtVerifier().fetchJwks("http://tool.example.com/jwks.json"));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
    }

    @Test
    void keySetThatIsNotJwks_isBadGateway() throws Exception {
        doReturn("{\"not\":\"a key set\"}").when(verifier).fetchJwks(JWKS_URL);

        LtiReturnException e = rejected(signed(b -> { }));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
        assertEquals(Constants.DEEPLINK_JWKS_UNAVAILABLE, e.getMessage());
    }
}
