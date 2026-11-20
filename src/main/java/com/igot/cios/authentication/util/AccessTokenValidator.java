package com.igot.cios.authentication.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cios.authentication.model.KeyData;
import com.igot.cios.util.CbServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

/**
 * Verifies a portal user token (the {@code x-authenticated-user-token} header) offline and extracts the user id.
 * Same behaviour as the validator in the external enrollment service: RS256 signature checked against the realm
 * public key named by the header's {@code kid}, not expired, issuer equal to {@code <sso.url>/realms/<sso.realm>},
 * and the user id is the part of {@code sub} after the last ':'.
 */
@Slf4j
@Component
public class AccessTokenValidator {

    public static final String UNAUTHORIZED = "Unauthorized";
    private static final String SHA_256_WITH_RSA = "SHA256withRSA";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KeyManager keyManager;
    private final String realmUrl;

    @Autowired
    public AccessTokenValidator(KeyManager keyManager, CbServerProperties cbServerProperties) {
        this.keyManager = keyManager;
        this.realmUrl = StringUtils.removeEnd(StringUtils.defaultString(cbServerProperties.getKeycloakUrl()).trim(), "/")
                + "/realms/" + cbServerProperties.getSsoRealm();
    }

    /** @return the token's body when the signature is valid and the token is not expired, otherwise an empty map */
    private Map<String, Object> validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid token format");
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            Map<String, Object> header = MAPPER.readValue(decoder.decode(parts[0]), new TypeReference<Map<String, Object>>() {
            });
            Object keyId = header.get("kid");
            KeyData keyData = keyId == null ? null : keyManager.getPublicKey(keyId.toString());
            if (keyData == null) {
                log.warn("AccessTokenValidator::validateToken: no public key loaded for the token's kid");
                return Collections.emptyMap();
            }
            boolean valid = CryptoUtil.verifyRSASign(parts[0] + "." + parts[1], decoder.decode(parts[2]),
                    keyData.getPublicKey(), SHA_256_WITH_RSA);
            if (!valid) {
                return Collections.emptyMap();
            }
            Map<String, Object> body = MAPPER.readValue(decoder.decode(parts[1]), new TypeReference<Map<String, Object>>() {
            });
            Object exp = body.get("exp");
            if (!(exp instanceof Number) || System.currentTimeMillis() / 1000 > ((Number) exp).longValue()) {
                log.warn("AccessTokenValidator::validateToken: token expired or has no exp");
                return Collections.emptyMap();
            }
            return body;
        } catch (Exception e) {
            log.error("AccessTokenValidator::validateToken: unable to validate the token: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** @return the user id, or {@link #UNAUTHORIZED} when the token cannot be verified */
    public String verifyUserToken(String token) {
        String userId = UNAUTHORIZED;
        if (StringUtils.isBlank(token)) {
            return userId;
        }
        try {
            Map<String, Object> payload = validateToken(token);
            if (!payload.isEmpty() && checkIss((String) payload.get("iss"))) {
                String sub = (String) payload.get("sub");
                if (StringUtils.isNotBlank(sub)) {
                    userId = sub.substring(sub.lastIndexOf(':') + 1);
                }
            }
        } catch (Exception e) {
            log.error("AccessTokenValidator::verifyUserToken: ", e);
        }
        return userId;
    }

    /** @return the user id, or null when the token is missing, invalid or expired */
    public String fetchUserIdFromAccessToken(String accessToken) {
        String userId = verifyUserToken(accessToken);
        return UNAUTHORIZED.equalsIgnoreCase(userId) ? null : userId;
    }

    private boolean checkIss(String iss) {
        if (!realmUrl.equalsIgnoreCase(iss)) {
            log.warn("AccessTokenValidator::checkIss: issuer {} does not match the expected {}", iss, realmUrl);
            return false;
        }
        return true;
    }
}
