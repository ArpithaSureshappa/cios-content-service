package com.igot.cios.lti.ags.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * IMS LTI Advantage AGS Score resource, as POSTed by the Tool -
 * application/vnd.ims.lis.v1.score+json.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreDto {
    private String userId;
    private Double scoreGiven;
    private Double scoreMaximum;
    private String comment;
    private String activityProgress;
    private String gradingProgress;
    private String timestamp;
}
