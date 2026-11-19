package com.igot.cios.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lti_score_entity")
@IdClass(LtiScoreEntityId.class)
public class LtiScoreEntity implements Serializable {
    @Id
    private String lineItemId;
    @Id
    private String userId;

    private String partnerCode;

    private Double scoreGiven;

    private Double scoreMaximum;

    private String comment;

    private String activityProgress;

    private String gradingProgress;

    private Timestamp timestamp;

    private Timestamp createdDate;

    private Timestamp updatedDate;
}
