package com.igot.cios.lti.ags.service;

import com.igot.cios.lti.ags.dto.ScoreDto;

public interface AgsService {

    void publishScore(String partnerCode, String lineItemId, ScoreDto scoreDto);
}
