package com.igot.cios.lti.ags.service;

import com.igot.cios.entity.LtiLineItemEntity;
import com.igot.cios.entity.LtiScoreEntity;
import com.igot.cios.lti.ags.dto.LineItemDto;
import com.igot.cios.lti.ags.dto.ResultDto;
import com.igot.cios.lti.ags.dto.ScoreDto;
import com.igot.cios.lti.ags.exception.AgsApiException;
import com.igot.cios.repository.LtiLineItemRepository;
import com.igot.cios.repository.LtiScoreRepository;
import com.igot.cios.util.CbServerProperties;
import com.igot.cios.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AgsServiceImpl implements AgsService {

    private final LtiLineItemRepository lineItemRepository;
    private final LtiScoreRepository scoreRepository;
    private final CbServerProperties cbServerProperties;

    @Autowired
    public AgsServiceImpl(LtiLineItemRepository lineItemRepository, LtiScoreRepository scoreRepository,
                           CbServerProperties cbServerProperties) {
        this.lineItemRepository = lineItemRepository;
        this.scoreRepository = scoreRepository;
        this.cbServerProperties = cbServerProperties;
    }

    @Override
    public LineItemDto createLineItem(String partnerCode, LineItemDto lineItemDto) {
        if (lineItemDto.getScoreMaximum() == null) {
            throw new AgsApiException(HttpStatus.BAD_REQUEST, Constants.MISSING_SCORE_MAXIMUM);
        }
        if (StringUtils.isBlank(lineItemDto.getLabel())) {
            throw new AgsApiException(HttpStatus.BAD_REQUEST, Constants.MISSING_LABEL);
        }

        LtiLineItemEntity entity = new LtiLineItemEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setPartnerCode(partnerCode);
        entity.setResourceLinkId(lineItemDto.getResourceLinkId());
        entity.setResourceId(lineItemDto.getResourceId());
        entity.setLabel(lineItemDto.getLabel());
        entity.setScoreMaximum(lineItemDto.getScoreMaximum());
        entity.setTag(lineItemDto.getTag());
        entity.setStartDateTime(parseTimestamp(lineItemDto.getStartDateTime()));
        entity.setEndDateTime(parseTimestamp(lineItemDto.getEndDateTime()));
        Timestamp now = new Timestamp(System.currentTimeMillis());
        entity.setCreatedDate(now);
        entity.setUpdatedDate(now);
        lineItemRepository.save(entity);
        return toDto(entity);
    }

    @Override
    public Page<LineItemDto> listLineItems(String partnerCode, String resourceLinkId, String resourceId, String tag, int limit, int page) {
        Pageable pageable = PageRequest.of(page, limit);
        return lineItemRepository.findByFilters(partnerCode, resourceLinkId, resourceId, tag, pageable).map(this::toDto);
    }

    @Override
    public LineItemDto getLineItem(String partnerCode, String lineItemId) {
        return toDto(findLineItemOrThrow(partnerCode, lineItemId));
    }

    @Override
    public LineItemDto updateLineItem(String partnerCode, String lineItemId, LineItemDto lineItemDto) {
        LtiLineItemEntity entity = findLineItemOrThrow(partnerCode, lineItemId);
        if (lineItemDto.getScoreMaximum() != null) {
            entity.setScoreMaximum(lineItemDto.getScoreMaximum());
        }
        if (StringUtils.isNotBlank(lineItemDto.getLabel())) {
            entity.setLabel(lineItemDto.getLabel());
        }
        entity.setResourceId(lineItemDto.getResourceId());
        entity.setResourceLinkId(lineItemDto.getResourceLinkId());
        entity.setTag(lineItemDto.getTag());
        entity.setStartDateTime(parseTimestamp(lineItemDto.getStartDateTime()));
        entity.setEndDateTime(parseTimestamp(lineItemDto.getEndDateTime()));
        entity.setUpdatedDate(new Timestamp(System.currentTimeMillis()));
        lineItemRepository.save(entity);
        return toDto(entity);
    }

    @Override
    public void deleteLineItem(String partnerCode, String lineItemId) {
        lineItemRepository.delete(findLineItemOrThrow(partnerCode, lineItemId));
    }

    @Override
    public void publishScore(String partnerCode, String lineItemId, ScoreDto scoreDto) {
        findLineItemOrThrow(partnerCode, lineItemId);
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

    @Override
    public Page<ResultDto> listResults(String partnerCode, String lineItemId, String userId, int limit, int page) {
        LtiLineItemEntity lineItem = findLineItemOrThrow(partnerCode, lineItemId);
        Pageable pageable = PageRequest.of(page, limit);
        if (StringUtils.isNotBlank(userId)) {
            return scoreRepository.findByLineItemIdAndUserId(lineItemId, userId)
                    .map(score -> new PageImpl<>(List.of(toResultDto(score, lineItem)), pageable, 1))
                    .orElseGet(() -> new PageImpl<>(List.of(), pageable, 0));
        }
        return scoreRepository.findByLineItemId(lineItemId, pageable).map(score -> toResultDto(score, lineItem));
    }

    private LtiLineItemEntity findLineItemOrThrow(String partnerCode, String lineItemId) {
        if (StringUtils.isBlank(lineItemId)) {
            throw new AgsApiException(HttpStatus.BAD_REQUEST, Constants.MISSING_LINE_ITEM_ID);
        }
        return lineItemRepository.findByIdAndPartnerCode(lineItemId, partnerCode)
                .orElseThrow(() -> new AgsApiException(HttpStatus.NOT_FOUND, Constants.LINE_ITEM_NOT_FOUND + lineItemId));
    }

    private LineItemDto toDto(LtiLineItemEntity entity) {
        LineItemDto dto = new LineItemDto();
        dto.setId(buildLineItemUrl(entity.getPartnerCode(), entity.getId()));
        dto.setScoreMaximum(entity.getScoreMaximum());
        dto.setLabel(entity.getLabel());
        dto.setResourceId(entity.getResourceId());
        dto.setResourceLinkId(entity.getResourceLinkId());
        dto.setTag(entity.getTag());
        dto.setStartDateTime(formatTimestamp(entity.getStartDateTime()));
        dto.setEndDateTime(formatTimestamp(entity.getEndDateTime()));
        return dto;
    }

    private ResultDto toResultDto(LtiScoreEntity score, LtiLineItemEntity lineItem) {
        String lineItemUrl = buildLineItemUrl(lineItem.getPartnerCode(), lineItem.getId());
        ResultDto dto = new ResultDto();
        dto.setId(lineItemUrl + "/results/" + score.getUserId());
        dto.setScoreOf(lineItemUrl);
        dto.setUserId(score.getUserId());
        dto.setResultScore(score.getScoreGiven());
        dto.setResultMaximum(score.getScoreMaximum());
        dto.setComment(score.getComment());
        return dto;
    }

    private String buildLineItemUrl(String partnerCode, String lineItemId) {
        return cbServerProperties.getCiosPublicBaseUrl() + "/ciosIntegration/lti/ags/" + partnerCode + "/lineitems/" + lineItemId;
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

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }
}
