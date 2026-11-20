package com.igot.cios.authentication.model;

import java.security.PublicKey;

/** A Keycloak realm public key, identified by the token header's {@code kid} (which is also its file name). */
public class KeyData {
    private final String keyId;
    private final PublicKey publicKey;

    public KeyData(String keyId, PublicKey publicKey) {
        this.keyId = keyId;
        this.publicKey = publicKey;
    }

    public String getKeyId() {
        return keyId;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }
}
