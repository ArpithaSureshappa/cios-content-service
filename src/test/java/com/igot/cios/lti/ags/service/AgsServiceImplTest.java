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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgsServiceImplTest {

    private static final String PARTNER_CODE = "COURSERA";
    private static final String LINE_ITEM_ID = "line-item-1";
    private static final String BASE_URL = "https://portal.example.com/api";

    @Mock
    private LtiLineItemRepository lineItemRepository;

    @Mock
    private LtiScoreRepository scoreRepository;

    private AgsServiceImpl service;

    @BeforeEach
    void setUp() {
        CbServerProperties cbServerProperties = new CbServerProperties();
        cbServerProperties.setCiosPublicBaseUrl(BASE_URL);
        service = new AgsServiceImpl(lineItemRepository, scoreRepository, cbServerProperties);
    }

    private LineItemDto newLineItemDto() {
        LineItemDto dto = new LineItemDto();
        dto.setLabel("Week 1 Quiz");
        dto.setScoreMaximum(100.0);
        dto.setResourceLinkId("resource-link-1");
        return dto;
    }

    private LtiLineItemEntity existingLineItemEntity() {
        LtiLineItemEntity entity = new LtiLineItemEntity();
        entity.setId(LINE_ITEM_ID);
        entity.setPartnerCode(PARTNER_CODE);
        entity.setResourceLinkId("resource-link-1");
        entity.setLabel("Week 1 Quiz");
        entity.setScoreMaximum(100.0);
        return entity;
    }

    @Test
    void createLineItem_missingScoreMaximum_throwsBadRequest() {
        LineItemDto dto = newLineItemDto();
        dto.setScoreMaximum(null);

        AgsApiException ex = assertThrows(AgsApiException.class, () -> service.createLineItem(PARTNER_CODE, dto));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(lineItemRepository, never()).save(any());
    }

    @Test
    void createLineItem_missingLabel_throwsBadRequest() {
        LineItemDto dto = newLineItemDto();
        dto.setLabel("");

        AgsApiException ex = assertThrows(AgsApiException.class, () -> service.createLineItem(PARTNER_CODE, dto));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createLineItem_success_savesAndReturnsUrlBasedId() {
        LineItemDto result = service.createLineItem(PARTNER_CODE, newLineItemDto());

        ArgumentCaptor<LtiLineItemEntity> captor = ArgumentCaptor.forClass(LtiLineItemEntity.class);
        verify(lineItemRepository).save(captor.capture());
        LtiLineItemEntity saved = captor.getValue();
        assertEquals(PARTNER_CODE, saved.getPartnerCode());
        assertEquals("Week 1 Quiz", saved.getLabel());
        assertNotNull(saved.getId());

        assertTrue(result.getId().startsWith(BASE_URL + "/ciosIntegration/lti/ags/" + PARTNER_CODE + "/lineitems/"));
        assertEquals(100.0, result.getScoreMaximum());
    }

    @Test
    void getLineItem_notFound_throwsNotFound() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.empty());

        AgsApiException ex = assertThrows(AgsApiException.class, () -> service.getLineItem(PARTNER_CODE, LINE_ITEM_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void getLineItem_found_returnsDto() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(existingLineItemEntity()));

        LineItemDto dto = service.getLineItem(PARTNER_CODE, LINE_ITEM_ID);

        assertEquals(BASE_URL + "/ciosIntegration/lti/ags/" + PARTNER_CODE + "/lineitems/" + LINE_ITEM_ID, dto.getId());
        assertEquals("Week 1 Quiz", dto.getLabel());
    }

    @Test
    void updateLineItem_notFound_throwsNotFound() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.empty());

        assertThrows(AgsApiException.class, () -> service.updateLineItem(PARTNER_CODE, LINE_ITEM_ID, newLineItemDto()));
    }

    @Test
    void updateLineItem_success_updatesScoreMaximumAndLabel() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(existingLineItemEntity()));
        LineItemDto update = new LineItemDto();
        update.setScoreMaximum(50.0);
        update.setLabel("Week 2 Quiz");

        LineItemDto result = service.updateLineItem(PARTNER_CODE, LINE_ITEM_ID, update);

        assertEquals(50.0, result.getScoreMaximum());
        assertEquals("Week 2 Quiz", result.getLabel());
        verify(lineItemRepository).save(any(LtiLineItemEntity.class));
    }

    @Test
    void deleteLineItem_notFound_throwsNotFound() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.empty());

        assertThrows(AgsApiException.class, () -> service.deleteLineItem(PARTNER_CODE, LINE_ITEM_ID));
    }

    @Test
    void deleteLineItem_success_deletesEntity() {
        LtiLineItemEntity entity = existingLineItemEntity();
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(entity));

        service.deleteLineItem(PARTNER_CODE, LINE_ITEM_ID);

        verify(lineItemRepository).delete(entity);
    }

    @Test
    void publishScore_lineItemNotFound_throwsNotFound() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.empty());
        ScoreDto score = new ScoreDto();
        score.setUserId("user-1");

        assertThrows(AgsApiException.class, () -> service.publishScore(PARTNER_CODE, LINE_ITEM_ID, score));
        verify(scoreRepository, never()).save(any());
    }

    @Test
    void publishScore_missingUserId_throwsBadRequest() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(existingLineItemEntity()));
        ScoreDto score = new ScoreDto();

        AgsApiException ex = assertThrows(AgsApiException.class, () -> service.publishScore(PARTNER_CODE, LINE_ITEM_ID, score));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void publishScore_newScore_savesWithScoreFields() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(existingLineItemEntity()));
        when(scoreRepository.findByLineItemIdAndUserId(LINE_ITEM_ID, "user-1")).thenReturn(Optional.empty());
        ScoreDto score = new ScoreDto();
        score.setUserId("user-1");
        score.setScoreGiven(100.0);
        score.setScoreMaximum(100.0);
        score.setActivityProgress("Completed");
        score.setGradingProgress("FullyGraded");

        service.publishScore(PARTNER_CODE, LINE_ITEM_ID, score);

        ArgumentCaptor<LtiScoreEntity> captor = ArgumentCaptor.forClass(LtiScoreEntity.class);
        verify(scoreRepository).save(captor.capture());
        LtiScoreEntity saved = captor.getValue();
        assertEquals(LINE_ITEM_ID, saved.getLineItemId());
        assertEquals("user-1", saved.getUserId());
        assertEquals(100.0, saved.getScoreGiven());
        assertEquals("Completed", saved.getActivityProgress());
        assertNotNull(saved.getCreatedDate());
        assertNotNull(saved.getUpdatedDate());
    }

    @Test
    void publishScore_existingScore_preservesCreatedDateOnUpsert() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(existingLineItemEntity()));
        LtiScoreEntity existing = new LtiScoreEntity();
        existing.setLineItemId(LINE_ITEM_ID);
        existing.setUserId("user-1");
        Timestamp originalCreatedDate = new Timestamp(1000L);
        existing.setCreatedDate(originalCreatedDate);
        when(scoreRepository.findByLineItemIdAndUserId(LINE_ITEM_ID, "user-1")).thenReturn(Optional.of(existing));
        ScoreDto score = new ScoreDto();
        score.setUserId("user-1");
        score.setScoreGiven(80.0);

        service.publishScore(PARTNER_CODE, LINE_ITEM_ID, score);

        ArgumentCaptor<LtiScoreEntity> captor = ArgumentCaptor.forClass(LtiScoreEntity.class);
        verify(scoreRepository).save(captor.capture());
        assertEquals(originalCreatedDate, captor.getValue().getCreatedDate());
        assertEquals(80.0, captor.getValue().getScoreGiven());
    }

    @Test
    void listResults_withUserId_found_returnsSingleResult() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(existingLineItemEntity()));
        LtiScoreEntity scoreEntity = new LtiScoreEntity();
        scoreEntity.setLineItemId(LINE_ITEM_ID);
        scoreEntity.setUserId("user-1");
        scoreEntity.setScoreGiven(90.0);
        scoreEntity.setScoreMaximum(100.0);
        when(scoreRepository.findByLineItemIdAndUserId(LINE_ITEM_ID, "user-1")).thenReturn(Optional.of(scoreEntity));

        Page<ResultDto> results = service.listResults(PARTNER_CODE, LINE_ITEM_ID, "user-1", 50, 0);

        assertEquals(1, results.getTotalElements());
        ResultDto dto = results.getContent().get(0);
        assertEquals("user-1", dto.getUserId());
        assertEquals(90.0, dto.getResultScore());
        assertEquals(BASE_URL + "/ciosIntegration/lti/ags/" + PARTNER_CODE + "/lineitems/" + LINE_ITEM_ID, dto.getScoreOf());
    }

    @Test
    void listResults_withUserId_notFound_returnsEmptyPage() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(existingLineItemEntity()));
        when(scoreRepository.findByLineItemIdAndUserId(LINE_ITEM_ID, "missing-user")).thenReturn(Optional.empty());

        Page<ResultDto> results = service.listResults(PARTNER_CODE, LINE_ITEM_ID, "missing-user", 50, 0);

        assertTrue(results.getContent().isEmpty());
    }

    @Test
    void listResults_withoutUserId_returnsPaginatedResults() {
        when(lineItemRepository.findByIdAndPartnerCode(LINE_ITEM_ID, PARTNER_CODE)).thenReturn(Optional.of(existingLineItemEntity()));
        LtiScoreEntity scoreEntity = new LtiScoreEntity();
        scoreEntity.setLineItemId(LINE_ITEM_ID);
        scoreEntity.setUserId("user-1");
        scoreEntity.setScoreGiven(90.0);
        Pageable pageable = PageRequest.of(0, 50);
        Page<LtiScoreEntity> page = new PageImpl<>(List.of(scoreEntity), pageable, 1);
        when(scoreRepository.findByLineItemId(eq(LINE_ITEM_ID), any(Pageable.class))).thenReturn(page);

        Page<ResultDto> results = service.listResults(PARTNER_CODE, LINE_ITEM_ID, null, 50, 0);

        assertEquals(1, results.getTotalElements());
        assertFalse(results.hasNext());
    }
}
