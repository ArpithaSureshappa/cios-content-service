package com.igot.cios.lti.ags.service;

import com.igot.cios.lti.ags.dto.LineItemDto;
import com.igot.cios.lti.ags.dto.ResultDto;
import com.igot.cios.lti.ags.dto.ScoreDto;
import org.springframework.data.domain.Page;

public interface AgsService {

    LineItemDto createLineItem(String partnerCode, LineItemDto lineItemDto);

    Page<LineItemDto> listLineItems(String partnerCode, String resourceLinkId, String resourceId, String tag, int limit, int page);

    LineItemDto getLineItem(String partnerCode, String lineItemId);

    LineItemDto updateLineItem(String partnerCode, String lineItemId, LineItemDto lineItemDto);

    void deleteLineItem(String partnerCode, String lineItemId);

    void publishScore(String partnerCode, String lineItemId, ScoreDto scoreDto);

    Page<ResultDto> listResults(String partnerCode, String lineItemId, String userId, int limit, int page);
}
