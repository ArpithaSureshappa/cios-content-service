package com.igot.cios.authentication.util;

import com.igot.cios.authentication.model.KeyData;
import com.igot.cios.util.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AccessTokenValidatorTest {

    private static final String REALM = "https://portal.example.org/auth/realms/sunbird";

    @Mock
    private KeyManager keyManager;

    private KeyPair keyPair;
    private AccessTokenValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        lenient().when(keyManager.getPublicKey("kid-1")).thenReturn(new KeyData("kid-1", keyPair.getPublic()));
        // trailing slash on sso.url must not matter
        CbServerProperties properties = new CbServerProperties();
        properties.setKeycloakUrl("https://portal.example.org/auth/");
        properties.setSsoRealm("sunbird");
        validator = new AccessTokenValidator(keyManager, properties);
    }

    private String token(String kid, String sub, String iss, long exp, KeyPair signer) throws Exception {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString(("{\"alg\":\"RS256\",\"kid\":\"" + kid + "\"}").getBytes(StandardCharsets.UTF_8));
        String body = enc.encodeToString(("{\"sub\":\"" + sub + "\",\"iss\":\"" + iss + "\",\"exp\":" + exp + "}")
                .getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(signer.getPrivate());
        signature.update((header + "." + body).getBytes(StandardCharsets.US_ASCII));
        return header + "." + body + "." + enc.encodeToString(signature.sign());
    }

    private long inOneHour() {
        return System.currentTimeMillis() / 1000 + 3600;
    }

    @Test
    void validTokenReturnsUserIdAfterLastColon() throws Exception {
        String token = token("kid-1", "f:abc-component:user-123", REALM, inOneHour(), keyPair);
        assertEquals("user-123", validator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = token("kid-1", "f:c:user-123", REALM, System.currentTimeMillis() / 1000 - 10, keyPair);
        assertNull(validator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String token = token("kid-1", "f:c:user-123", REALM, inOneHour(), generator.generateKeyPair());
        assertNull(validator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        String token = token("kid-1", "f:c:user-123", "https://evil.example.org/auth/realms/sunbird", inOneHour(), keyPair);
        assertNull(validator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void unknownKeyIdIsRejected() throws Exception {
        String token = token("unknown-kid", "f:c:user-123", REALM, inOneHour(), keyPair);
        assertNull(validator.fetchUserIdFromAccessToken(token));
    }

    @Test
    void malformedOrMissingTokenIsRejected() {
        assertNull(validator.fetchUserIdFromAccessToken(null));
        assertNull(validator.fetchUserIdFromAccessToken(""));
        assertNull(validator.fetchUserIdFromAccessToken("not-a-jwt"));
        assertEquals(AccessTokenValidator.UNAUTHORIZED, validator.verifyUserToken("a.b"));
    }
}
