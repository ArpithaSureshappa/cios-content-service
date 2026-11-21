package com.igot.cios.lti.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.igot.cios.util.Constants;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies the signed LtiDeepLinkingResponse a Tool posts back and extracts the selected item.
 * Nothing in the token is trusted until the signature has been verified against the keys the
 * Tool published at the partner's own stored {@code toolPublicJwksUrl}.
 */
@Slf4j
@Component
public class DeepLinkingJwtVerifier {

    static final long CLOCK_SKEW_MS = 60_000L;
    static final long JWKS_CACHE_TTL_MS = 5 * 60_000L;
    /**
     * A key id missing from a cache entry newer than this does not trigger another download.
     */
    static final long JWKS_MIN_REFRESH_MS = 30_000L;
    static final int JWKS_TIMEOUT_MS = 5000;
    static final int MAX_JWKS_BYTES = 64 * 1024;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();

    private record CachedJwks(JWKSet keys, long fetchedAtMs) {
    }

    /**
     * @return a one-element array holding the validated content item
     * @throws LtiReturnException with the status to answer: 400 malformed, 401 bad signature,
     *                            403 wrong claims, 502 the Tool's keys are unavailable
     */
    public ArrayNode verifyAndExtractItems(String jwt, String partnerCode, String clientId, String deploymentId,
                                           String platformIssuer, String jwksUrl) {
        SignedJWT signedJwt = parse(jwt);
        JWK key = findKey(signedJwt.getHeader().getKeyID(), jwksUrl);
        verifySignature(signedJwt, key);
        JWTClaimsSet claims = readClaims(signedJwt);
        validateClaims(claims, partnerCode, clientId, deploymentId, platformIssuer);
        return validateItems(claims);
    }

    private SignedJWT parse(String jwt) {
        SignedJWT parsed;
        try {
            parsed = SignedJWT.parse(jwt);
        } catch (ParseException e) {
            throw new LtiReturnException(HttpStatus.BAD_REQUEST, Constants.DEEPLINK_JWT_MALFORMED);
        }
        if (!JWSAlgorithm.RS256.equals(parsed.getHeader().getAlgorithm())) {
            throw new LtiReturnException(HttpStatus.BAD_REQUEST, Constants.DEEPLINK_JWT_UNSUPPORTED_ALG);
        }
        if (StringUtils.isBlank(parsed.getHeader().getKeyID())) {
            throw new LtiReturnException(HttpStatus.BAD_REQUEST, Constants.DEEPLINK_JWT_MISSING_KID);
        }
        return parsed;
    }

    private JWK findKey(String kid, String jwksUrl) {
        JWK key = jwkSet(jwksUrl, kid).getKeyByKeyId(kid);
        if (key == null || !(key instanceof RSAKey)
                || (key.getKeyUse() != null && !KeyUse.SIGNATURE.equals(key.getKeyUse()))) {
            throw new LtiReturnException(HttpStatus.BAD_REQUEST, Constants.DEEPLINK_JWT_KID_NOT_FOUND);
        }
        return key;
    }

    private JWKSet jwkSet(String jwksUrl, String kid) {
        long now = System.currentTimeMillis();
        CachedJwks cached = jwksCache.get(jwksUrl);
        if (cached != null) {
            long age = now - cached.fetchedAtMs();
            if (age < JWKS_CACHE_TTL_MS && cached.keys().getKeyByKeyId(kid) != null) {
                return cached.keys();
            }
            if (age < JWKS_MIN_REFRESH_MS) {
                return cached.keys();
            }
        }
        JWKSet fresh;
        try {
            fresh = JWKSet.parse(fetchJwks(jwksUrl));
        } catch (ParseException e) {
            log.error("DeepLinkingJwtVerifier::jwkSet: the key set at {} is not valid JWKS", jwksUrl);
            throw new LtiReturnException(HttpStatus.BAD_GATEWAY, Constants.DEEPLINK_JWKS_UNAVAILABLE);
        }
        jwksCache.put(jwksUrl, new CachedJwks(fresh, now));
        return fresh;
    }

    private void verifySignature(SignedJWT jwt, JWK key) {
        try {
            if (!jwt.verify(new RSASSAVerifier(((RSAKey) key).toRSAPublicKey()))) {
                throw new LtiReturnException(HttpStatus.UNAUTHORIZED, Constants.DEEPLINK_JWT_SIGNATURE_INVALID);
            }
        } catch (JOSEException e) {
            throw new LtiReturnException(HttpStatus.UNAUTHORIZED, Constants.DEEPLINK_JWT_SIGNATURE_INVALID);
        }
    }

    private JWTClaimsSet readClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new LtiReturnException(HttpStatus.BAD_REQUEST, Constants.DEEPLINK_JWT_MALFORMED);
        }
    }

    private void validateClaims(JWTClaimsSet claims, String partnerCode, String clientId, String deploymentId,
                                String platformIssuer) {
        if (!clientId.equals(claims.getIssuer())) {
            throw forbidden(Constants.DEEPLINK_CLAIM_ISSUER_MISMATCH);
        }
        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(platformIssuer)) {
            throw forbidden(Constants.DEEPLINK_CLAIM_AUDIENCE_MISMATCH);
        }
        if (!deploymentId.equals(stringClaim(claims, Constants.DEEPLINK_DEPLOYMENT_ID_CLAIM))) {
            throw forbidden(Constants.DEEPLINK_CLAIM_DEPLOYMENT_MISMATCH);
        }
        if (!Constants.DEEPLINK_MESSAGE_TYPE_RESPONSE.equals(stringClaim(claims, Constants.DEEPLINK_MESSAGE_TYPE_CLAIM))) {
            throw forbidden(Constants.DEEPLINK_CLAIM_MESSAGE_TYPE_INVALID);
        }
        if (!Constants.LTI_PROTOCOL_VERSION_CLAIM_VALUE.equals(stringClaim(claims, Constants.DEEPLINK_VERSION_CLAIM))) {
            throw forbidden(Constants.DEEPLINK_CLAIM_VERSION_INVALID);
        }
        long now = System.currentTimeMillis();
        Date expiry = claims.getExpirationTime();
        if (expiry == null || now > expiry.getTime() + CLOCK_SKEW_MS) {
            throw forbidden(Constants.DEEPLINK_CLAIM_EXPIRED);
        }
        Date issuedAt = claims.getIssueTime();
        if (issuedAt == null || issuedAt.getTime() > now + CLOCK_SKEW_MS) {
            throw forbidden(Constants.DEEPLINK_CLAIM_ISSUED_IN_FUTURE);
        }
        if (StringUtils.isBlank(stringClaim(claims, Constants.DEEPLINK_NONCE_CLAIM))) {
            throw forbidden(Constants.DEEPLINK_CLAIM_NONCE_MISSING);
        }
        // The partner travels in both the request param and the signed data claim; they must agree.
        if (!(Constants.LTI_DEEPLINK_HINT_PREFIX + partnerCode).equals(stringClaim(claims, Constants.DEEPLINK_DATA_CLAIM))) {
            throw forbidden(Constants.DEEPLINK_CLAIM_DATA_MISMATCH);
        }
    }

    private ArrayNode validateItems(JWTClaimsSet claims) {
        Object raw = claims.getClaim(Constants.DEEPLINK_CONTENT_ITEMS_CLAIM);
        if (!(raw instanceof List<?> list) || list.size() != 1) {
            throw invalidItems();
        }
        JsonNode item = mapper.valueToTree(list.get(0));
        if (!item.isObject()
                || !Constants.LTI_RESOURCE_LINK_ITEM_TYPE.equals(item.path(Constants.DEEPLINK_ITEM_TYPE_FIELD).asText(""))
                || StringUtils.isBlank(item.path(Constants.DEEPLINK_ITEM_TITLE_FIELD).asText(""))
                || !isHttpsUrl(item.path(Constants.DEEPLINK_ITEM_URL_FIELD).asText(""))
                || !item.path(Constants.DEEPLINK_ITEM_CUSTOM_FIELD).isObject()) {
            throw invalidItems();
        }
        ArrayNode items = mapper.createArrayNode();
        items.add(item);
        return items;
    }

    String fetchJwks(String jwksUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(jwksUrl).toURL();
            if (!Constants.HTTPS_SCHEME.equalsIgnoreCase(url.getProtocol())) {
                throw new LtiReturnException(HttpStatus.BAD_GATEWAY, Constants.DEEPLINK_JWKS_UNAVAILABLE);
            }
            for (InetAddress address : InetAddress.getAllByName(url.getHost())) {
                if (LtiServiceImpl.isDisallowedAddress(address)) {
                    log.error("DeepLinkingJwtVerifier::fetchJwks: refusing to fetch keys from disallowed host {}", url.getHost());
                    throw new LtiReturnException(HttpStatus.BAD_GATEWAY, Constants.DEEPLINK_JWKS_UNAVAILABLE);
                }
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(JWKS_TIMEOUT_MS);
            connection.setReadTimeout(JWKS_TIMEOUT_MS);
            connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.error("DeepLinkingJwtVerifier::fetchJwks: key endpoint {} answered {}", jwksUrl, connection.getResponseCode());
                throw new LtiReturnException(HttpStatus.BAD_GATEWAY, Constants.DEEPLINK_JWKS_UNAVAILABLE);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_JWKS_BYTES) {
                        throw new LtiReturnException(HttpStatus.BAD_GATEWAY, Constants.DEEPLINK_JWKS_UNAVAILABLE);
                    }
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException | IllegalArgumentException e) {
            log.error("DeepLinkingJwtVerifier::fetchJwks: failed to fetch the key set from {}", jwksUrl, e);
            throw new LtiReturnException(HttpStatus.BAD_GATEWAY, Constants.DEEPLINK_JWKS_UNAVAILABLE);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException e) {
            return null;
        }
    }

    private static boolean isHttpsUrl(String url) {
        try {
            URI uri = URI.create(url);
            return Constants.HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) && StringUtils.isNotBlank(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static LtiReturnException forbidden(String message) {
        return new LtiReturnException(HttpStatus.FORBIDDEN, message);
    }

    private static LtiReturnException invalidItems() {
        return new LtiReturnException(HttpStatus.BAD_REQUEST, Constants.DEEPLINK_CONTENT_ITEMS_INVALID);
    }
}
