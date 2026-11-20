package com.igot.cios.authentication.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;

@Slf4j
public final class CryptoUtil {

    private CryptoUtil() {
    }

    /** Verifies an RSA signature over {@code payload} (ASCII bytes of {@code header.body}). */
    public static boolean verifyRSASign(String payload, byte[] signature, PublicKey key, String algorithm) {
        try {
            Signature sign = Signature.getInstance(algorithm);
            sign.initVerify(key);
            sign.update(payload.getBytes(StandardCharsets.US_ASCII));
            return sign.verify(signature);
        } catch (GeneralSecurityException e) {
            log.error("CryptoUtil::verifyRSASign: signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
