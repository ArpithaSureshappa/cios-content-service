package com.igot.cios.lti.service;

import com.igot.cios.dto.SBApiResponse;

import java.util.List;
import java.util.Map;

public interface LtiService {

    SBApiResponse testOidcConnection(String partnerCode, String loginHint, String messageHint);

    SBApiResponse handleDeepLinkingReturn(String jwt);

    List<Map<String, Object>> getLineItems();

    Map<String, Object> getLineItem(String lineItemId);
}
