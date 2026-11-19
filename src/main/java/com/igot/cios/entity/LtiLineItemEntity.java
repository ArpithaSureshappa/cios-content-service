package com.igot.cios.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lti_line_item_entity")
public class LtiLineItemEntity {
    @Id
    private String id;

    private String partnerCode;

    private String resourceLinkId;

    private String resourceId;

    private String label;

    private Double scoreMaximum;

    private String tag;

    private Timestamp startDateTime;

    private Timestamp endDateTime;

    private Timestamp createdDate;

    private Timestamp updatedDate;
}
