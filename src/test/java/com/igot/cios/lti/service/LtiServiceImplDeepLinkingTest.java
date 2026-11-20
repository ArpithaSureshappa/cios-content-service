package com.igot.cios.lti.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cios.dto.SBApiResponse;
import com.igot.cios.plugins.DataTransformUtility;
import com.igot.cios.util.Constants;
import org.igot.common.auth.AccessTokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LtiServiceImplDeepLinkingTest {

    private static final String PARTNER_CODE = "IGOT-COURSERA-ABCDE";
    private static final String PARTNER_ID = "7c1f1c2e-0000-4000-8000-000000000001";
    private static final String ADMIN_ID = "admin-user-1";
    private static final String USER_TOKEN = "header.payload.signature";
    private static final String PLATFORM_ISSUER = "https://portal.example.org/auth/realms/sunbird";
    private static final String LOGIN_URL = "https://tool.example.com/api/tool-lti/login";
    private static final String DEEP_LINKING_URL = "https://tool.example.com/api/tool-lti/deep-linking/launch";
    private static final String REDIRECT_LAUNCH_URL = "https://tool.example.com/api/tool-lti/launch";
    private static final String JWKS_URL = "https://tool.example.com/.well-known/jwks.json";

    @Mock
    private DataTransformUtility dataTransformUtility;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private AccessTokenValidator accessTokenValidator;

    private final ObjectMapper mapper = new ObjectMapper();
    private LtiServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LtiServiceImpl(dataTransformUtility, restTemplate, accessTokenValidator, null);
        lenient().when(accessTokenValidator.fetchUserIdFromAccessToken(USER_TOKEN)).thenReturn(ADMIN_ID);
    }

    // ---------- helpers ----------

    private ObjectNode validLti() {
        ObjectNode lti = mapper.createObjectNode();
        lti.put(Constants.LTI_VERSION, Constants.LTI_VERSION_1_3);
        lti.put(Constants.OIDC_LOGIN_INITIATION_URL, LOGIN_URL);
        lti.put(Constants.REDIRECT_LAUNCH_URL, REDIRECT_LAUNCH_URL);
        lti.put(Constants.DEEP_LINKING_URL, DEEP_LINKING_URL);
        lti.put(Constants.TOOL_PUBLIC_JWKS_URL, JWKS_URL);
        lti.put(Constants.CLIENT_ID, "portal");
        lti.put(Constants.DEPLOYMENT_ID, PARTNER_CODE);
        lti.put(Constants.TOOL_ISSUER, PLATFORM_ISSUER);
        return lti;
    }

    private ObjectNode partner(ObjectNode lti) {
        ObjectNode result = mapper.createObjectNode();
        result.put(Constants.ID, PARTNER_ID);
        ObjectNode data = result.putObject(Constants.DATA);
        data.put(Constants.PROTOCOL, Constants.LTI);
        if (lti != null) {
            data.set(Constants.LTI_CONFIGURATION, lti);
        }
        return result;
    }

    private void givenPartner(ObjectNode lti) {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(partner(lti));
    }

    private Map<String, String> queryParamsOf(String url) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().toSingleValueMap().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        e -> UriUtils.decode(e.getValue(), StandardCharsets.UTF_8)));
    }

    // ---------- positive ----------

    @Test
    void validConfiguration_returnsOnlyDeeplinkUrl() {
        givenPartner(validLti());

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertEquals(Constants.API_LTI_DEEP_LINKING_INITIATE, response.getId());
        assertTrue(response.containsKey(Constants.DEEP_LINK_URL));
        assertEquals(1, response.getResult().size(), "result must carry only deeplinkUrl");
        assertEquals("deeplinkUrl", Constants.DEEP_LINK_URL);
    }

    @Test
    void deeplinkUrlCarriesTheDeepLinkingParametersFromTrustedConfiguration() {
        givenPartner(validLti());

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);
        String launchUrl = response.get(Constants.DEEP_LINK_URL).toString();
        assertTrue(launchUrl.startsWith(LOGIN_URL + "?"), launchUrl);
        Map<String, String> params = queryParamsOf(launchUrl);
        assertEquals(PLATFORM_ISSUER, params.get(Constants.ISS));
        assertEquals(ADMIN_ID, params.get(Constants.LOGIN_HINT));
        assertEquals(DEEP_LINKING_URL, params.get(Constants.TARGET_LINK_URI));
        assertEquals("portal", params.get(Constants.CLIENT_ID_QUERY_PARAM));
        assertEquals(PARTNER_CODE, params.get(Constants.LTI_DEPLOYMENT_ID));
        assertEquals("Deeplink:" + PARTNER_CODE, params.get(Constants.LTI_MESSAGE_HINT));
        assertEquals(6, params.size());
    }

    @Test
    void targetLinkUriIsTheDeepLinkingUrlNotTheResourceLaunchUrl() {
        givenPartner(validLti());

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        String target = queryParamsOf(response.get(Constants.DEEP_LINK_URL).toString()).get(Constants.TARGET_LINK_URI);
        assertEquals(DEEP_LINKING_URL, target);
        assertNotEquals(REDIRECT_LAUNCH_URL, target);
    }

    @Test
    void existingQueryOfLoginUrlIsPreservedAndValuesAreStrictlyEncoded() {
        ObjectNode lti = validLti();
        lti.put(Constants.OIDC_LOGIN_INITIATION_URL, "https://tool.example.com/login?id=abc123");
        lti.put(Constants.DEEP_LINKING_URL, "https://tool.example.com/dl?a=1&b=2+3");
        lti.put(Constants.CLIENT_ID, "client id&x=1");
        lti.put(Constants.DEPLOYMENT_ID, "dep+1/é");
        givenPartner(lti);

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        String launchUrl = response.get(Constants.DEEP_LINK_URL).toString();
        assertTrue(launchUrl.startsWith("https://tool.example.com/login?id=abc123&iss="), launchUrl);
        Map<String, String> params = queryParamsOf(launchUrl);
        assertEquals("abc123", params.get("id"));
        assertEquals("https://tool.example.com/dl?a=1&b=2+3", params.get(Constants.TARGET_LINK_URI));
        assertEquals("client id&x=1", params.get(Constants.CLIENT_ID_QUERY_PARAM));
        assertEquals("dep+1/é", params.get(Constants.LTI_DEPLOYMENT_ID));
        assertFalse(launchUrl.contains("&x=1"), "an '&' inside a value must not start a new parameter");
        assertFalse(launchUrl.contains(" "));
    }

    @Test
    void responseSerializesWithItsResultThroughSpringJsonDefaults() throws Exception {
        givenPartner(validLti());
        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        String json = Jackson2ObjectMapperBuilder.json().build().writeValueAsString(response);
        var tree = mapper.readTree(json);

        assertEquals("OK", tree.path("responseCode").asText());
        assertTrue(tree.path("result").has("deeplinkUrl"), json);
        assertFalse(tree.path("result").has("transactionId"), json);
        assertFalse(tree.path("result").has("expiresAt"), json);
    }

    // ---------- negative: input ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void blankPartnerCodeIsRejected(String partnerCode) {
        SBApiResponse response = service.initiateDeepLinking(partnerCode, USER_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.MISSING_PARTNER_CODE, response.getParams().getErrmsg());
        verifyNoInteractions(dataTransformUtility);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void missingOrUnverifiableTokenIsRejected(String token) {
        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, token);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        assertEquals(Constants.AUTHENTICATED_USER_NOT_FOUND, response.getParams().getErrmsg());
        verifyNoInteractions(dataTransformUtility);
    }

    // ---------- negative: partner lookup ----------

    @Test
    void unknownPartnerWithEmptyResultIsNotFound() {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(mapper.createObjectNode());

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.PARTNER_NOT_FOUND + PARTNER_CODE, response.getParams().getErrmsg());
    }

    @Test
    void nullPartnerResultIsNotFound() {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(null);

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void partnerServiceFailureIsReportedAsBadGatewayWithoutDetails() {
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE))
                .thenThrow(new RuntimeException("Failed to get data from API: Response is null"));

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getResponseCode());
        assertEquals(Constants.PARTNER_SERVICE_UNAVAILABLE, response.getParams().getErrmsg());
    }

    // ---------- negative: configuration ----------

    static Stream<Arguments> invalidConfigurations() {
        return Stream.of(
                Arguments.of("unsupported version", (Consumer<ObjectNode>) l -> l.put(Constants.LTI_VERSION, "1.1"),
                        Constants.UNSUPPORTED_LTI_VERSION),
                Arguments.of("missing version", (Consumer<ObjectNode>) l -> l.remove(Constants.LTI_VERSION),
                        Constants.UNSUPPORTED_LTI_VERSION),
                Arguments.of("missing client id", (Consumer<ObjectNode>) l -> l.remove(Constants.CLIENT_ID),
                        Constants.MISSING_CLIENT_ID),
                Arguments.of("blank client id", (Consumer<ObjectNode>) l -> l.put(Constants.CLIENT_ID, " "),
                        Constants.MISSING_CLIENT_ID),
                Arguments.of("missing deployment id", (Consumer<ObjectNode>) l -> l.remove(Constants.DEPLOYMENT_ID),
                        Constants.MISSING_DEPLOYMENT_ID),
                Arguments.of("missing login url", (Consumer<ObjectNode>) l -> l.remove(Constants.OIDC_LOGIN_INITIATION_URL),
                        Constants.MISSING_OIDC_LOGIN_INITIATION_URL),
                Arguments.of("http login url", (Consumer<ObjectNode>) l -> l.put(Constants.OIDC_LOGIN_INITIATION_URL, "http://tool.example.com/login"),
                        Constants.INVALID_OIDC_LOGIN_INITIATION_URL),
                Arguments.of("malformed login url", (Consumer<ObjectNode>) l -> l.put(Constants.OIDC_LOGIN_INITIATION_URL, "https://tool example/login"),
                        Constants.INVALID_OIDC_LOGIN_INITIATION_URL),
                Arguments.of("invalid redirect launch url", (Consumer<ObjectNode>) l -> l.put(Constants.REDIRECT_LAUNCH_URL, "ftp://x"),
                        Constants.MISSING_REDIRECT_LAUNCH_URL),
                Arguments.of("missing issuer", (Consumer<ObjectNode>) l -> l.remove(Constants.TOOL_ISSUER),
                        Constants.MISSING_TOOL_ISSUER),
                Arguments.of("http issuer", (Consumer<ObjectNode>) l -> l.put(Constants.TOOL_ISSUER, "http://portal.example.org/realm"),
                        Constants.INVALID_TOOL_ISSUER),
                Arguments.of("missing deep linking url", (Consumer<ObjectNode>) l -> l.remove(Constants.DEEP_LINKING_URL),
                        Constants.MISSING_DEEP_LINKING_URL),
                Arguments.of("http deep linking url", (Consumer<ObjectNode>) l -> l.put(Constants.DEEP_LINKING_URL, "http://tool.example.com/dl"),
                        Constants.INVALID_DEEP_LINKING_URL),
                Arguments.of("missing jwks url", (Consumer<ObjectNode>) l -> l.remove(Constants.TOOL_PUBLIC_JWKS_URL),
                        Constants.MISSING_TOOL_PUBLIC_JWKS_URL),
                Arguments.of("invalid jwks url", (Consumer<ObjectNode>) l -> l.put(Constants.TOOL_PUBLIC_JWKS_URL, "not a url"),
                        Constants.INVALID_TOOL_PUBLIC_JWKS_URL));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidConfigurations")
    void invalidConfigurationIsRejectedAndNothingIsPersisted(String name, Consumer<ObjectNode> mutation, String expectedMessage) {
        ObjectNode lti = validLti();
        mutation.accept(lti);
        givenPartner(lti);

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(expectedMessage + PARTNER_CODE, response.getParams().getErrmsg());
        assertFalse(response.containsKey(Constants.DEEP_LINK_URL));
    }

    @Test
    void nonLtiPartnerIsRejected() {
        ObjectNode result = partner(validLti());
        ((ObjectNode) result.path(Constants.DATA)).put(Constants.PROTOCOL, "SAML");
        when(dataTransformUtility.fetchPartnerInfoUsingApi(PARTNER_CODE)).thenReturn(result);

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.PARTNER_PROTOCOL_NOT_LTI + PARTNER_CODE, response.getParams().getErrmsg());
    }

    @Test
    void missingLtiConfigurationIsRejected() {
        givenPartner(null);

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.LTI_CONFIGURATION_NOT_FOUND + PARTNER_CODE, response.getParams().getErrmsg());
    }

    // ---------- negative: reliability ----------

    @Test
    void urlBuildFailureReturnsInternalServerError() {
        ObjectNode lti = validLti();
        // accepted by java.net.URI, rejected when the login URL is assembled
        lti.put(Constants.OIDC_LOGIN_INITIATION_URL, "https://tool.example.com/lógin");
        givenPartner(lti);

        SBApiResponse response = service.initiateDeepLinking(PARTNER_CODE, USER_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.DEEP_LINK_URL_BUILD_FAILED + PARTNER_CODE, response.getParams().getErrmsg());
        assertFalse(response.containsKey(Constants.DEEP_LINK_URL));
    }
}
