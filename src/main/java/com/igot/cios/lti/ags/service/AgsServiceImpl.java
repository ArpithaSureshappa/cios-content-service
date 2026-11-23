package com.igot.cios.lti.ags.service;

import com.igot.cios.entity.LtiScoreEntity;
import com.igot.cios.lti.ags.dto.ScoreDto;
import com.igot.cios.lti.ags.exception.AgsApiException;
import com.igot.cios.repository.LtiScoreRepository;
import com.igot.cios.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Slf4j
@Service
public class AgsServiceImpl implements AgsService {

    private final LtiScoreRepository scoreRepository;

    @Autowired
    public AgsServiceImpl(LtiScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    @Override
    public void publishScore(String partnerCode, String lineItemId, ScoreDto scoreDto) {
        if (StringUtils.isBlank(lineItemId)) {
            throw new AgsApiException(HttpStatus.BAD_REQUEST, Constants.MISSING_LINE_ITEM_ID);
        }
        if (StringUtils.isBlank(scoreDto.getUserId())) {
            throw new AgsApiException(HttpStatus.BAD_REQUEST, Constants.MISSING_USER_ID);
        }

        LtiScoreEntity entity = scoreRepository.findByLineItemIdAndUserId(lineItemId, scoreDto.getUserId())
                .orElseGet(LtiScoreEntity::new);
        boolean isNew = entity.getLineItemId() == null;
        entity.setLineItemId(lineItemId);
        entity.setUserId(scoreDto.getUserId());
        entity.setPartnerCode(partnerCode);
        entity.setScoreGiven(scoreDto.getScoreGiven());
        entity.setScoreMaximum(scoreDto.getScoreMaximum());
        entity.setComment(scoreDto.getComment());
        entity.setActivityProgress(scoreDto.getActivityProgress());
        entity.setGradingProgress(scoreDto.getGradingProgress());
        entity.setTimestamp(parseTimestamp(scoreDto.getTimestamp()));
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (isNew) {
            entity.setCreatedDate(now);
        }
        entity.setUpdatedDate(now);
        scoreRepository.save(entity);
    }

    private Timestamp parseTimestamp(String isoDateTime) {
        if (StringUtils.isBlank(isoDateTime)) {
            return null;
        }
        try {
            return Timestamp.from(Instant.parse(isoDateTime));
        } catch (Exception e) {
            throw new AgsApiException(HttpStatus.BAD_REQUEST, "Invalid ISO-8601 date-time: " + isoDateTime);
        }
    }
}
