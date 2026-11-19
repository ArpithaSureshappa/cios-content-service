package com.igot.cios.lti.ags.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * IMS LTI Advantage AGS Result resource - application/vnd.ims.lis.v2.resultcontainer+json
 * (the container is a plain JSON array of these).
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResultDto {
    private String id;
    private String scoreOf;
    private String userId;
    private Double resultScore;
    private Double resultMaximum;
    private String comment;
}
