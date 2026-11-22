package com.igot.cios.lti.ags.service;

import com.igot.cios.lti.ags.dto.LineItemDto;
import com.igot.cios.lti.ags.dto.ResultDto;
import com.igot.cios.lti.ags.dto.ScoreDto;
import org.springframework.data.domain.Page;

public interface AgsService {

    LineItemDto createLineItem(String partnerCode, LineItemDto lineItemDto);

    void publishScore(String partnerCode, String lineItemId, ScoreDto scoreDto);
}
