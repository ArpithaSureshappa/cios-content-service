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
 * One registered LTI Tool (a provider such as Moodle).
 *
 * <p>For Moodle this is <b>one row per Moodle site</b>, not per course. Moodle
 * uses a single site-level registration and then exposes its published courses
 * through the Deep Linking content picker.
 *
 * <p>Note the direction of the URL fields: {@code initiateLoginUri},
 * {@code redirectUris} and {@code jwksUri} all belong to the <b>Tool</b>. We use
 * them to start a launch and to verify signatures the Tool produces. iGOT's own
 * signing key lives in {@link LtiPlatformKeyEntity}.
 *
 * <p>Several rows may share a {@code partnerCode} — they are the same partner
 * organisation with distinct LTI registrations.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lti_provider_registration")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LtiProviderRegistrationEntity implements Serializable {

    @Id
    private String id;

    private String partnerCode;

    private String partnerId;

    private String providerName;

    /** The client_id WE issue to this tool. Unique across all registrations. */
    @Column(unique = true)
    private String clientId;

    private String deploymentId;

    /** The tool's own issuer, when it declares one. */
    private String toolIssuer;

    @Column(columnDefinition = "TEXT")
    private String initiateLoginUri;

    /** Comma-separated; tools frequently register more than one. */
    @Column(columnDefinition = "TEXT")
    private String redirectUris;

    @Column(columnDefinition = "TEXT")
    private String jwksUri;

    /** Null when the tool exposes no content picker. */
    @Column(columnDefinition = "TEXT")
    private String deepLinkingUri;

    /** sandbox | production */
    private String environment;

    /** active | suspended */
    private String status;

    private Boolean isActive;

    private Timestamp createdDate;

    private Timestamp updatedDate;
}
