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
 * iGOT's own RSA signing keypair.
 *
 * <p><b>Global, not per registration.</b> Every tool fetches the same JWKS URL
 * and selects a key by {@code kid}. Registering a new provider does not create
 * a key — only {@code clientId} differs per provider.
 *
 * <p>Multiple rows exist for <b>rotation lifecycle</b>, not for multiplicity of
 * tools: typically 2–3 rows at any time regardless of how many providers are
 * registered. JWKS publishes {@code active} and {@code rotating} keys so tokens
 * signed just before a switch still verify.
 *
 * <p><b>SECURITY — open item.</b> {@code privateKeyJwk} currently holds key
 * material directly. Production must move this to a secrets manager and keep
 * only a reference here. Tracked as an open decision in the LTI HLD; do not
 * promote this as-is.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "lti_platform_key")
@JsonIgnoreProperties(ignoreUnknown = true)
public class LtiPlatformKeyEntity implements Serializable {

    /** Key id published in the JWKS and set in the JWS header. */
    @Id
    private String kid;

    private String algorithm;

    /** Public JWK, safe to serve. */
    @Column(columnDefinition = "TEXT")
    private String publicKeyJwk;

    /** Full JWK including private members. See the security note above. */
    @Column(columnDefinition = "TEXT")
    private String privateKeyJwk;

    /** active | rotating | retired */
    private String status;

    private Timestamp createdDate;

    private Timestamp activatedDate;

    private Timestamp retiredDate;
}
