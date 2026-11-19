package com.igot.cios.lti.ags.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * IMS LTI Advantage AGS Line Item resource - application/vnd.ims.lis.v2.lineitem+json.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LineItemDto {
    private String id;
    private Double scoreMaximum;
    private String label;
    private String resourceId;
    private String resourceLinkId;
    private String tag;
    private String startDateTime;
    private String endDateTime;
}
