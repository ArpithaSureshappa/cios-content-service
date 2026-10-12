package com.igot.cios.lti.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Binds a catalog content item to the provider registration that launches it.
 *
 * <p>{@code externalId} + {@code partnerId} deliberately mirror
 * {@link com.igot.cios.entity.CornellContentEntity}'s composite key, so the two
 * join naturally.
 *
 * <p>{@code targetLinkUri} lives here rather than reusing
 * {@code CornellContentEntity.redirectUrl} on purpose. {@code redirectUrl} is
 * rendered directly as an {@code href} by the portal; an LTI target link must
 * never be opened as a plain hyperlink — it is only valid as the destination of
 * a signed launch. Keeping them in separate columns makes that impossible to
 * get wrong by accident.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lti_resource_link")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LtiResourceLinkEntity implements Serializable {

    @Id
    private String id;

    private String externalId;

    private String partnerId;

    private String registrationId;

    @Column(columnDefinition = "TEXT")
    private String targetLinkUri;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String customJson;

    private Double scoreMaximum;

    private Timestamp createdDate;

    private Timestamp updatedDate;
}
