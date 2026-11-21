package com.igot.cios.lti.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cios.dto.SBApiResponse;
import com.igot.cios.plugins.DataTransformUtility;
import com.igot.cios.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LtiServiceImplDeepLinkingReturnTest {

    private static final String PARTNER_CODE = "IGOT-COURSERA-ABCDE";
    private static final String PARTNER_ID = "7c1f1c2e-0000-4000-8000-000000000001";
    private static final String PLATFORM_ISSUER = "https://portal.example.org/auth/realms/sunbird";
    private static final String JWKS_URL = "https://tool.example.com/.well-known/jwks.json";
    private static final String JWT = "header.payload.signature";

    @Mock
    private DataTransformUtility dataTransformUtility;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private DeepLinkingJwtVerifier verifier;

    private final ObjectMapper mapper = new ObjectMapper();
    private LtiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LtiServiceImpl(dataTransformUtility, restTemplate, null, verifier);
    }

    // ---------- helpers ----------

    private ObjectNode validLti() {
        ObjectNode lti = mapper.createObjectNode();
        lti.put(Constants.LTI_VERSION, Constants.LTI_VERSION_1_3);
        lti.put(Constants.TOOL_PUBLIC_JWKS_URL, JWKS_URL);
        lti.put(Constants.CLIENT_ID, "portal");
        lti.put(Constants.DEPLOYMENT_ID, PARTNER_CODE);
        lti.put(Constants.TOOL_ISSUER, PLATFORM_ISSUER);
        return lti;
    }

    private ObjectNode partner(String protocol, ObjectNode lti) {
        ObjectNode result = mapper.createObjectNode();
        result.put(Constants.ID, PARTNER_ID);
        ObjectNode data = result.putObject(Constants.DATA);
        data.put(Constants.PROTOCOL, protocol);
        if (lti != null) {
            data.set(Constants.LTI_CONFIGURATION, lti);
        }
        return result;
    }

    private void givenPartner(ObjectNode lti) {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(partner(Constants.LTI, lti));
    }

    private void assertFailed(SBApiResponse response, HttpStatus status) {
        assertEquals(status, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.API_LTI_DEEPLINK_RETURN, response.getId());
        verifyNoInteractions(verifier);
    }

    // ---------- positive ----------

    @Test
    void validResponse_returnsPartnerAndContentItems() {
        givenPartner(validLti());
        ArrayNode items = mapper.createArrayNode().add(mapper.createObjectNode().put("title", "AI For Everyone"));
        when(verifier.verifyAndExtractItems(JWT, PARTNER_CODE, "portal", PARTNER_CODE, PLATFORM_ISSUER, JWKS_URL))
                .thenReturn(items);

        SBApiResponse response = service.handleDeepLinkingReturn(JWT, PARTNER_CODE);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(Constants.API_LTI_DEEPLINK_RETURN, response.getId());
        assertEquals(PARTNER_CODE, response.get(Constants.PARTNER_CODE));
        assertSame(items, response.get(Constants.CONTENT_ITEMS));
    }

    // ---------- request validation: 400 ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingJwt_isBadRequest(String jwt) {
        SBApiResponse response = service.handleDeepLinkingReturn(jwt, PARTNER_CODE);

        assertFailed(response, HttpStatus.BAD_REQUEST);
        assertEquals(Constants.MISSING_JWT, response.getParams().getErrmsg());
        verifyNoInteractions(dataTransformUtility);
    }

    @Test
    void oversizedJwt_isBadRequest() {
        SBApiResponse response = service.handleDeepLinkingReturn("a".repeat(Constants.MAX_DEEPLINK_JWT_LENGTH + 1), PARTNER_CODE);

        assertFailed(response, HttpStatus.BAD_REQUEST);
        assertEquals(Constants.DEEPLINK_JWT_TOO_LONG, response.getParams().getErrmsg());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingPartnerCode_isBadRequest(String partnerCode) {
        SBApiResponse response = service.handleDeepLinkingReturn(JWT, partnerCode);

        assertFailed(response, HttpStatus.BAD_REQUEST);
        assertEquals(Constants.MISSING_PARTNER_CODE, response.getParams().getErrmsg());
        verifyNoInteractions(dataTransformUtility);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc/passwd", "CODE/../X", "CODE?x=1", "CODE X", "CODE\nX"})
    void partnerCodeWithUnexpectedCharacters_isBadRequest(String partnerCode) {
        SBApiResponse response = service.handleDeepLinkingReturn(JWT, partnerCode);

        assertFailed(response, HttpStatus.BAD_REQUEST);
        assertEquals(Constants.INVALID_PARTNER_CODE, response.getParams().getErrmsg());
        verifyNoInteractions(dataTransformUtility);
    }

    @Test
    void partnerCodeLongerThanTheLimit_isBadRequest() {
        SBApiResponse response = service.handleDeepLinkingReturn(JWT, "A".repeat(101));

        assertFailed(response, HttpStatus.BAD_REQUEST);
        assertEquals(Constants.INVALID_PARTNER_CODE, response.getParams().getErrmsg());
        verifyNoInteractions(dataTransformUtility);
    }

    // ---------- partner lookup ----------

    @Test
    void partnerServiceDown_isBadGateway() {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenThrow(new RuntimeException("boom"));

        SBApiResponse response = service.handleDeepLinkingReturn(JWT, PARTNER_CODE);

        assertFailed(response, HttpStatus.BAD_GATEWAY);
        assertEquals(Constants.PARTNER_SERVICE_UNAVAILABLE, response.getParams().getErrmsg());
    }

    @Test
    void unknownPartner_isNotFound() {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(mapper.createObjectNode());

        SBApiResponse response = service.handleDeepLinkingReturn(JWT, PARTNER_CODE);

        assertFailed(response, HttpStatus.NOT_FOUND);
        assertEquals(Constants.PARTNER_NOT_FOUND + PARTNER_CODE, response.getParams().getErrmsg());
    }

    @Test
    void nullPartnerResponse_isNotFound() {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(null);

        assertFailed(service.handleDeepLinkingReturn(JWT, PARTNER_CODE), HttpStatus.NOT_FOUND);
    }

    // ---------- stored configuration: 400 ----------

    @Test
    void partnerThatIsNotLti_isBadRequest() {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(partner("SAML", validLti()));

        SBApiResponse response = service.handleDeepLinkingReturn(JWT, PARTNER_CODE);

        assertFailed(response, HttpStatus.BAD_REQUEST);
        assertEquals(Constants.PARTNER_PROTOCOL_NOT_LTI + PARTNER_CODE, response.getParams().getErrmsg());
    }

    @Test
    void partnerWithoutLtiConfiguration_isBadRequest() {
        givenPartner(null);

        SBApiResponse response = service.handleDeepLinkingReturn(JWT, PARTNER_CODE);

        assertFailed(response, HttpStatus.BAD_REQUEST);
        assertEquals(Constants.LTI_CONFIGURATION_NOT_FOUND + PARTNER_CODE, response.getParams().getErrmsg());
    }

    private void assertConfigRejected(Consumer<ObjectNode> breakIt, String expectedMessage) {
        ObjectNode lti = validLti();
        breakIt.accept(lti);
        givenPartner(lti);

        SBApiResponse response = service.handleDeepLinkingReturn(JWT, PARTNER_CODE);

        assertFailed(response, HttpStatus.BAD_REQUEST);
        assertEquals(expectedMessage + PARTNER_CODE, response.getParams().getErrmsg());
    }

    @Test
    void unsupportedLtiVersion_isBadRequest() {
        assertConfigRejected(l -> l.put(Constants.LTI_VERSION, "1.1"), Constants.UNSUPPORTED_LTI_VERSION);
    }

    @Test
    void missingClientId_isBadRequest() {
        assertConfigRejected(l -> l.remove(Constants.CLIENT_ID), Constants.MISSING_CLIENT_ID);
    }

    @Test
    void missingDeploymentId_isBadRequest() {
        assertConfigRejected(l -> l.remove(Constants.DEPLOYMENT_ID), Constants.MISSING_DEPLOYMENT_ID);
    }

    @Test
    void missingToolIssuer_isBadRequest() {
        assertConfigRejected(l -> l.remove(Constants.TOOL_ISSUER), Constants.MISSING_TOOL_ISSUER);
    }

    @Test
    void insecureToolIssuer_isBadRequest() {
        assertConfigRejected(l -> l.put(Constants.TOOL_ISSUER, "http://portal.example.org"), Constants.INVALID_TOOL_ISSUER);
    }

    @Test
    void missingJwksUrl_isBadRequest() {
        assertConfigRejected(l -> l.remove(Constants.TOOL_PUBLIC_JWKS_URL), Constants.MISSING_TOOL_PUBLIC_JWKS_URL);
    }

    @Test
    void insecureJwksUrl_isBadRequest() {
        assertConfigRejected(l -> l.put(Constants.TOOL_PUBLIC_JWKS_URL, "http://tool.example.com/jwks"),
                Constants.INVALID_TOOL_PUBLIC_JWKS_URL);
    }

    // ---------- verifier outcomes are passed through ----------

    @Test
    void rejectionFromTheVerifier_keepsItsStatusAndMessage() {
        givenPartner(validLti());
        when(verifier.verifyAndExtractItems(any(), any(), any(), any(), any(), any()))
                .thenThrow(new LtiReturnException(HttpStatus.UNAUTHORIZED, Constants.DEEPLINK_JWT_SIGNATURE_INVALID));

        SBApiResponse response = service.handleDeepLinkingReturn(JWT, PARTNER_CODE);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.DEEPLINK_JWT_SIGNATURE_INVALID, response.getParams().getErrmsg());
        assertFalse(response.containsKey(Constants.CONTENT_ITEMS));
    }

    @Test
    void unexpectedVerifierFailure_isInternalErrorWithoutDetails() {
        givenPartner(validLti());
        when(verifier.verifyAndExtractItems(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("secret internal detail"));

        SBApiResponse response = service.handleDeepLinkingReturn(JWT, PARTNER_CODE);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.DEEPLINK_PROCESSING_FAILED, response.getParams().getErrmsg());
        assertFalse(response.getParams().getErrmsg().contains("secret"));
    }
}
