package com.igot.cios.lti.controller;

import com.igot.cios.lti.config.LtiPlatformProperties;
import com.igot.cios.lti.entity.LtiPlatformKeyEntity;
import com.igot.cios.lti.entity.LtiProviderRegistrationEntity;
import com.igot.cios.lti.entity.LtiResourceLinkEntity;
import com.igot.cios.lti.repository.LtiProviderRegistrationRepository;
import com.igot.cios.lti.repository.LtiResourceLinkRepository;
import com.igot.cios.lti.service.LtiPlatformKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrative endpoints: provider registry, key lifecycle, and the platform
 * configuration block an operator copies into the provider.
 *
 * <p>The registry lives here for now so the module is self-contained. The HLD
 * places {@code lti_provider_registration} in {@code cb-pores-service} beside
 * {@code ContentPartnerEntity} — moving it is a follow-up, and the repository
 * interface is the only seam that would change.
 *
 * <p><b>Pending:</b> these endpoints carry no authorisation. Gate them behind an
 * admin role before any shared environment.
 */
@RestController
@RequestMapping("/ciosIntegration/v1/lti/admin")
@RequiredArgsConstructor
public class LtiAdminController {

    private final LtiProviderRegistrationRepository registrationRepository;
    private final LtiResourceLinkRepository resourceLinkRepository;
    private final LtiPlatformKeyService keyService;
    private final LtiPlatformProperties properties;

    /**
     * The values an operator pastes into the provider.
     *
     * <p>All four are global — identical for every provider. Only the client id,
     * returned when a registration is created, differs.
     */
    @GetMapping("/platform-config")
    public Map<String, Object> platformConfig() {
        return Map.of(
                "issuer", properties.getIssuer(),
                "authenticationRequestUrl", properties.authorizeUrl(),
                "accessTokenUrl", properties.tokenUrl(),
                "publicKeysetUrl", properties.jwksUrl(),
                "activeKid", keyService.activeKid());
    }

    @PostMapping("/providers")
    public ResponseEntity<Map<String, Object>> create(@RequestBody LtiProviderRegistrationEntity request) {
        Timestamp now = Timestamp.from(Instant.now());

        request.setId(UUID.randomUUID().toString());
        if (request.getClientId() == null || request.getClientId().isBlank()) {
            request.setClientId("igot-" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (request.getDeploymentId() == null || request.getDeploymentId().isBlank()) {
            request.setDeploymentId("1");
        }
        if (request.getEnvironment() == null) {
            request.setEnvironment("sandbox");
        }
        request.setStatus("active");
        request.setIsActive(true);
        request.setCreatedDate(now);
        request.setUpdatedDate(now);

        LtiProviderRegistrationEntity saved = registrationRepository.save(request);

        // Returned together so the operator has everything needed for the
        // provider-side form in one response.
        return ResponseEntity.ok(Map.of(
                "registration", saved,
                "platformConfigForProvider", platformConfig()));
    }

    @GetMapping("/providers")
    public List<LtiProviderRegistrationEntity> list() {
        return registrationRepository.findAll();
    }

    @GetMapping("/providers/{id}")
    public ResponseEntity<LtiProviderRegistrationEntity> read(@PathVariable String id) {
        return registrationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/providers/{id}")
    public ResponseEntity<LtiProviderRegistrationEntity> update(
            @PathVariable String id, @RequestBody LtiProviderRegistrationEntity request) {

        return registrationRepository.findById(id).map(existing -> {
            request.setId(id);
            request.setCreatedDate(existing.getCreatedDate());
            request.setUpdatedDate(Timestamp.from(Instant.now()));
            return ResponseEntity.ok(registrationRepository.save(request));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/providers/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        registrationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Manual catalog entry — the fallback when a provider exposes no content picker. */
    @PostMapping("/resource-links")
    public LtiResourceLinkEntity createResourceLink(@RequestBody LtiResourceLinkEntity request) {
        Timestamp now = Timestamp.from(Instant.now());
        request.setId(UUID.randomUUID().toString());
        request.setCreatedDate(now);
        request.setUpdatedDate(now);
        return resourceLinkRepository.save(request);
    }

    @GetMapping("/resource-links")
    public List<LtiResourceLinkEntity> listResourceLinks() {
        return resourceLinkRepository.findAll();
    }

    // ------------------------------------------------------------------ keys

    @GetMapping("/keys")
    public List<Map<String, Object>> keys() {
        // Never expose private key material over HTTP.
        return keyService.all().stream().map(k -> Map.<String, Object>of(
                "kid", k.getKid(),
                "algorithm", k.getAlgorithm(),
                "status", k.getStatus(),
                "createdDate", String.valueOf(k.getCreatedDate()))).toList();
    }

    @PostMapping("/keys/rotate")
    public Map<String, Object> rotate() {
        LtiPlatformKeyEntity fresh = keyService.rotate();
        return Map.of(
                "activeKid", fresh.getKid(),
                "note", "The previous key is now 'rotating' and still published in the JWKS "
                        + "so tokens signed before the switch continue to verify. "
                        + "Retire it once the id_token TTL has elapsed.");
    }

    @PostMapping("/keys/{kid}/retire")
    public ResponseEntity<Void> retire(@PathVariable String kid) {
        keyService.retire(kid);
        return ResponseEntity.noContent().build();
    }
}
