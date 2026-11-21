package com.igot.cios.lti.controller;

import com.igot.cios.dto.SBApiResponse;
import com.igot.cios.lti.service.LtiService;
import com.igot.cios.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(LtiController.class)
class LtiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LtiService ltiService;

    @Test
    void testOidcConnection_delegatesToServiceAndReturnsOk() throws Exception {
        SBApiResponse response = SBApiResponse.createDefaultResponse(Constants.API_LTI_OIDC_TEST);
        response.getParams().setStatus(Constants.SUCCESS);
        response.getParams().setErrmsg(Constants.ALL_CHECKS_PASSED);
        response.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.PARTNER_CODE, "IGOT-TESTINGLTI-OGDTD");
        response.setResult(result);

        Mockito.when(ltiService.testOidcConnection("IGOT-TESTINGLTI-OGDTD", null, null))
                .thenReturn(response);

        mockMvc.perform(get("/ciosIntegration/lti/ssotest/{partnerCode}", "IGOT-TESTINGLTI-OGDTD"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.params.status").value(Constants.SUCCESS))
                .andExpect(jsonPath("$.params.errmsg").value(Constants.ALL_CHECKS_PASSED))
                .andExpect(jsonPath("$.result.partnerCode").value("IGOT-TESTINGLTI-OGDTD"));
    }

    @Test
    void testOidcConnection_passesLoginHintAndMessageHintThrough() throws Exception {
        SBApiResponse response = SBApiResponse.createDefaultResponse(Constants.API_LTI_OIDC_TEST);
        response.setResponseCode(HttpStatus.OK);

        Mockito.when(ltiService.testOidcConnection(eq("IGOT-TESTINGLTI-OGDTD"), eq("demo-learner-1"), eq("test-hint")))
                .thenReturn(response);

        mockMvc.perform(get("/ciosIntegration/lti/ssotest/{partnerCode}", "IGOT-TESTINGLTI-OGDTD")
                        .param("loginHint", "demo-learner-1")
                        .param("messageHint", "test-hint"))
                .andExpect(status().isOk());

        Mockito.verify(ltiService).testOidcConnection("IGOT-TESTINGLTI-OGDTD", "demo-learner-1", "test-hint");
    }

    @Test
    void testOidcConnection_propagatesFailureResponseCode() throws Exception {
        SBApiResponse response = SBApiResponse.createDefaultResponse(Constants.API_LTI_OIDC_TEST);
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrmsg("Partner not found for code: UNKNOWN-PARTNER");
        response.setResponseCode(HttpStatus.OK);

        Mockito.when(ltiService.testOidcConnection(eq("UNKNOWN-PARTNER"), isNull(), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/ciosIntegration/lti/ssotest/{partnerCode}", "UNKNOWN-PARTNER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params.status").value(Constants.FAILED))
                .andExpect(jsonPath("$.params.errmsg").value("Partner not found for code: UNKNOWN-PARTNER"));
    }

    @Test
    void initiateDeepLinking_passesUserTokenHeaderAndReturnsResultUncached() throws Exception {
        SBApiResponse response = SBApiResponse.createDefaultResponse(Constants.API_LTI_DEEP_LINKING_INITIATE);
        response.put(Constants.DEEP_LINK_URL, "https://tool.example.com/login?lti_message_hint=abc");
        Mockito.when(ltiService.initiateDeepLinking("COURSERA", "user-token")).thenReturn(response);

        mockMvc.perform(get("/ciosIntegration/lti/deeplinking/initiate/{partnerCode}", "COURSERA")
                        .header(Constants.X_AUTH_TOKEN, "user-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.params.status").value(Constants.SUCCESS))
                .andExpect(jsonPath("$.result.deeplinkUrl").value("https://tool.example.com/login?lti_message_hint=abc"))
                .andExpect(jsonPath("$.result.transactionId").doesNotExist())
                .andExpect(jsonPath("$.result.expiresAt").doesNotExist());
    }

    @Test
    void initiateDeepLinking_withoutTokenHeaderPassesNullAndPropagatesStatus() throws Exception {
        SBApiResponse response = SBApiResponse.createDefaultResponse(Constants.API_LTI_DEEP_LINKING_INITIATE);
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrmsg(Constants.AUTHENTICATED_USER_NOT_FOUND);
        response.setResponseCode(HttpStatus.UNAUTHORIZED);
        Mockito.when(ltiService.initiateDeepLinking(eq("COURSERA"), isNull())).thenReturn(response);

        mockMvc.perform(get("/ciosIntegration/lti/deeplinking/initiate/{partnerCode}", "COURSERA"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.params.errmsg").value(Constants.AUTHENTICATED_USER_NOT_FOUND));
    }

    @Test
    void initiateDeepLinking_propagatesNotFoundFromService() throws Exception {
        SBApiResponse response = SBApiResponse.createDefaultResponse(Constants.API_LTI_DEEP_LINKING_INITIATE);
        response.getParams().setStatus(Constants.FAILED);
        response.setResponseCode(HttpStatus.NOT_FOUND);
        Mockito.when(ltiService.initiateDeepLinking("UNKNOWN", "user-token")).thenReturn(response);

        mockMvc.perform(get("/ciosIntegration/lti/deeplinking/initiate/{partnerCode}", "UNKNOWN")
                        .header(Constants.X_AUTH_TOKEN, "user-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deepLinkingReturn_passesJwtAndPartnerCodeAndReturnsResultUncached() throws Exception {
        SBApiResponse response = SBApiResponse.createDefaultResponse(Constants.API_LTI_DEEPLINK_RETURN);
        response.put(Constants.PARTNER_CODE, "COURSERA");
        Mockito.when(ltiService.handleDeepLinkingReturn("a.b.c", "COURSERA")).thenReturn(response);

        mockMvc.perform(post("/ciosIntegration/lti/deeplink/return")
                        .param("partnerCode", "COURSERA")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("JWT=a.b.c"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(Constants.API_LTI_DEEPLINK_RETURN))
                .andExpect(jsonPath("$.result.partnerCode").value("COURSERA"));
    }

    @Test
    void deepLinkingReturn_withoutParamsPassesNullsAndPropagatesStatus() throws Exception {
        SBApiResponse response = SBApiResponse.createDefaultResponse(Constants.API_LTI_DEEPLINK_RETURN);
        response.getParams().setStatus(Constants.FAILED);
        response.getParams().setErrmsg(Constants.MISSING_JWT);
        response.setResponseCode(HttpStatus.BAD_REQUEST);
        Mockito.when(ltiService.handleDeepLinkingReturn(isNull(), isNull())).thenReturn(response);

        mockMvc.perform(post("/ciosIntegration/lti/deeplink/return")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.errmsg").value(Constants.MISSING_JWT));
    }

    @Test
    void deepLinkingReturn_rejectsAJsonBodyWithoutCallingTheService() throws Exception {
        int httpStatus = mockMvc.perform(post("/ciosIntegration/lti/deeplink/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        assertTrue(httpStatus >= 400, "a non-form body must not be accepted, got " + httpStatus);
        Mockito.verifyNoInteractions(ltiService);
    }
}
