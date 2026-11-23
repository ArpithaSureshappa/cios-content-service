package com.igot.cios.lti.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cios.lti.controller.LtiController;
import com.igot.cios.plugins.DataTransformUtility;
import com.igot.cios.util.Constants;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whole return path with the real controller, service and verifier: a form POST carrying a signed
 * Deep Linking response and the partnerCode param. Only cb-pores and the Tool's key download are stubbed.
 */
class LtiDeepLinkingReturnFlowTest {

    private static final String PARTNER_CODE = "IGOT-COURSERATESTP-6OS93";
    private static final String PLATFORM_ISSUER = "https://portal.dev.karmayogibharat.net/auth/realms/sunbird";
    private static final String JWKS_URL = "https://www.coursera.org/api/rest/v1/tool-lti/.well-known/jwks.json";
    private static final String KID = "kid-1794183106";
    private static final String RETURN_PATH = "/ciosIntegration/lti/deeplink/return";

    private static RSAKey toolKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeAll
    static void generateKey() throws Exception {
        toolKey = new RSAKeyGenerator(2048).keyID(KID).generate();
    }

    @BeforeEach
    void setUp() {
        DataTransformUtility dataTransformUtility = spy(new DataTransformUtility());
        ReflectionTestUtils.setField(dataTransformUtility, "objectMapper", mapper);
        doReturn(courseraPartner()).when(dataTransformUtility).fetchPartnerInfoUsingApi(PARTNER_CODE);
        DeepLinkingJwtVerifier verifier = spy(new DeepLinkingJwtVerifier());
        doReturn(new JWKSet(toolKey.toPublicJWK()).toString()).when(verifier).fetchJwks(JWKS_URL);
        LtiServiceImpl service = new LtiServiceImpl(dataTransformUtility, mock(RestTemplate.class), null, verifier);
        mockMvc = MockMvcBuilders.standaloneSetup(new LtiController(service)).build();
    }

    /** The spec saved on the partner: Coursera's item to iGOT content. */
    private static final String COURSERA_SPEC = """
            [{"operation":"shift","spec":{
              "title":"content.name","text":"content.description","url":"content.redirectUrl",
              "custom":{"course_id":["content.externalId","content.providerCustomClaim.course_id"],
                        "*":"content.providerCustomClaim.&"}}}]
            """;

    private ObjectNode courseraPartner() {
        ObjectNode partner = mapper.createObjectNode();
        partner.put(Constants.ID, "7c1f1c2e-0000-4000-8000-000000000001");
        try {
            partner.set(Constants.TRANSFORM_CONTENT_VIA_API, mapper.readTree(COURSERA_SPEC));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        ObjectNode data = partner.putObject(Constants.DATA);
        data.put(Constants.PROTOCOL, Constants.LTI);
        ObjectNode lti = data.putObject(Constants.LTI_CONFIGURATION);
        lti.put(Constants.LTI_VERSION, Constants.LTI_VERSION_1_3);
        lti.put(Constants.TOOL_PUBLIC_JWKS_URL, JWKS_URL);
        lti.put(Constants.CLIENT_ID, "portal");
        lti.put(Constants.DEPLOYMENT_ID, PARTNER_CODE);
        lti.put(Constants.TOOL_ISSUER, PLATFORM_ISSUER);
        return partner;
    }

    /** Same shape Coursera returned in the live run. */
    private Map<String, Object> courseraItem() {
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("link_type", "COURSE");
        custom.put("course_id", "joRZVwp9Eeun_RJEc0KNDw");
        custom.put("organization_id", "38fNLqIAQ5C9FXQVtAwAOQ");
        custom.put("program_id", "XKGCT3amR96hgk92pufecQ");
        custom.put("coursera_source_context_id", null);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("lineItem", Map.of("scoreMaximum", 100.0));
        item.put("custom", custom);
        item.put("text", "This course is the seventh course in the Google Data Analytics Certificate.");
        item.put("window", Map.of("targetName", "_blank"));
        item.put("type", "ltiResourceLink");
        item.put("title", "Data Analysis with R Programming");
        item.put("url", "https://www.coursera.org/api/rest/v1/tool-lti/launch");
        return item;
    }

    private String signedResponse(String dataClaim) throws Exception {
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("portal")
                .audience(PLATFORM_ISSUER)
                .issueTime(new Date(now - 1_000))
                .expirationTime(new Date(now + 540_000))
                .claim(Constants.DEEPLINK_NONCE_CLAIM, "ZJiO-cl-evTF69Gj1vZFH65erk2fnX5QvXo0YzVa9to")
                .claim(Constants.DEEPLINK_MESSAGE_TYPE_CLAIM, Constants.DEEPLINK_MESSAGE_TYPE_RESPONSE)
                .claim(Constants.DEEPLINK_VERSION_CLAIM, Constants.LTI_PROTOCOL_VERSION_CLAIM_VALUE)
                .claim(Constants.DEEPLINK_DEPLOYMENT_ID_CLAIM, PARTNER_CODE)
                .claim(Constants.DEEPLINK_DATA_CLAIM, dataClaim)
                .claim(Constants.DEEPLINK_CONTENT_ITEMS_CLAIM, List.of(courseraItem()))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims);
        jwt.sign(new RSASSASigner(toolKey));
        return jwt.serialize();
    }

    @Test
    void formPostWithJwtAndPartnerCode_returnsTheItemMappedToIgotContent() throws Exception {
        mockMvc.perform(post(RETURN_PATH)
                        .param("partnerCode", PARTNER_CODE)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("JWT=" + signedResponse("Deeplink:" + PARTNER_CODE)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value("api.lti.deeplink.return"))
                .andExpect(jsonPath("$.params.status").value("success"))
                .andExpect(jsonPath("$.responseCode").value("OK"))
                .andExpect(jsonPath("$.result.partnerCode").value(PARTNER_CODE))
                .andExpect(jsonPath("$.result.contentData").isMap())
                .andExpect(jsonPath("$.result.contentData.content.name").value("Data Analysis with R Programming"))
                .andExpect(jsonPath("$.result.contentData.content.description")
                        .value("This course is the seventh course in the Google Data Analytics Certificate."))
                .andExpect(jsonPath("$.result.contentData.content.redirectUrl")
                        .value("https://www.coursera.org/api/rest/v1/tool-lti/launch"))
                .andExpect(jsonPath("$.result.contentData.content.externalId").value("joRZVwp9Eeun_RJEc0KNDw"))
                .andExpect(jsonPath("$.result.contentData.content.providerCustomClaim.course_id")
                        .value("joRZVwp9Eeun_RJEc0KNDw"))
                .andExpect(jsonPath("$.result.contentData.content.providerCustomClaim.organization_id")
                        .value("38fNLqIAQ5C9FXQVtAwAOQ"))
                .andExpect(jsonPath("$.result.contentData.content.providerCustomClaim.program_id")
                        .value("XKGCT3amR96hgk92pufecQ"))
                .andExpect(jsonPath("$.result.contentData.type").doesNotExist());
    }

    @Test
    void paramForAnotherPartner_isRejectedEvenWithAValidSignature() throws Exception {
        mockMvc.perform(post(RETURN_PATH)
                        .param("partnerCode", PARTNER_CODE)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("JWT=" + signedResponse("Deeplink:SOME-OTHER-PARTNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.params.errmsg").value(Constants.DEEPLINK_CLAIM_DATA_MISMATCH))
                .andExpect(jsonPath("$.result.contentData").doesNotExist());
    }

    @Test
    void missingJwt_isBadRequestWithTheStandardEnvelope() throws Exception {
        mockMvc.perform(post(RETURN_PATH)
                        .param("partnerCode", PARTNER_CODE)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.status").value("Failed"))
                .andExpect(jsonPath("$.params.errmsg").value(Constants.MISSING_JWT));
    }
}
