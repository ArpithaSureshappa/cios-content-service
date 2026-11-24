package com.igot.cios.plugins;

import com.bazaarvoice.jolt.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Applies a partner's Jolt spec the way the Deep Linking return API does, with the real transform code. */
class DataTransformUtilityPartnerSpecTest {

    private static final String COURSERA_ITEM = """
            {"lineItem":{"scoreMaximum":100},
             "custom":{"link_type":"COURSE","course_id":"joRZVwp9Eeun_RJEc0KNDw",
                       "organization_id":"38fNLqIAQ5C9FXQVtAwAOQ","program_id":"XKGCT3amR96hgk92pufecQ",
                       "coursera_source_context_id":null},
             "text":"This course is the seventh course in the Google Data Analytics Certificate.",
             "window":{"targetName":"_blank"},"type":"ltiResourceLink",
             "title":"Data Analysis with R Programming",
             "url":"https://www.coursera.org/api/rest/v1/tool-lti/launch"}
            """;
    private static final String COURSERA_SPEC = """
            [{"operation":"shift","spec":{
              "title":"content.name","text":"content.description","url":"content.redirectUrl",
              "custom":{"course_id":["content.externalId","content.providerCustomClaim.course_id"],
                        "*":"content.providerCustomClaim.&"}}}]
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void courseraItem_isMappedToIgotContentAndEveryCustomKeyIsKept() throws Exception {
        DataTransformUtility utility = new DataTransformUtility();
        ReflectionTestUtils.setField(utility, "objectMapper", mapper);
        List<Object> spec = JsonUtils.jsonToList(COURSERA_SPEC);

        JsonNode mapped = utility.transformData(mapper.readTree(COURSERA_ITEM), spec);

        assertEquals(mapper.readTree("""
                {"content":{
                  "name":"Data Analysis with R Programming",
                  "description":"This course is the seventh course in the Google Data Analytics Certificate.",
                  "redirectUrl":"https://www.coursera.org/api/rest/v1/tool-lti/launch",
                  "externalId":"joRZVwp9Eeun_RJEc0KNDw",
                  "providerCustomClaim":{"link_type":"COURSE","course_id":"joRZVwp9Eeun_RJEc0KNDw",
                    "organization_id":"38fNLqIAQ5C9FXQVtAwAOQ","program_id":"XKGCT3amR96hgk92pufecQ",
                    "coursera_source_context_id":null}}}
                """), mapped);
    }
}
