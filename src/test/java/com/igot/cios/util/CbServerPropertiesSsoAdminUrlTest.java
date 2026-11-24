package com.igot.cios.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The Keycloak admin APIs use sso.admin.url only, so the public sso.url can stay the token issuer that common-util
 * checks. sso.admin.url has no fallback and must be set in every environment.
 */
class CbServerPropertiesSsoAdminUrlTest {

    private static final String PUBLIC_URL = "https://portal.example.net/auth/";
    private static final String INTERNAL_URL = "http://10.0.0.1:8080/auth/";

    private String resolveKeycloakUrl(MockEnvironment environment) throws NoSuchFieldException {
        Value value = CbServerProperties.class.getField("keycloakUrl").getAnnotation(Value.class);
        return environment.resolveRequiredPlaceholders(value.value());
    }

    @Test
    void usesTheAdminUrlWhenItIsSet() throws NoSuchFieldException {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sso.url", PUBLIC_URL)
                .withProperty("sso.admin.url", INTERNAL_URL);
        assertEquals(INTERNAL_URL, resolveKeycloakUrl(environment));
    }

    @Test
    void doesNotUseTheSsoUrlForTheAdminApis() throws NoSuchFieldException {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sso.url", PUBLIC_URL)
                .withProperty("sso.admin.url", INTERNAL_URL);
        assertNotEquals(PUBLIC_URL, resolveKeycloakUrl(environment));
    }

    @Test
    void failsToResolveWhenTheAdminUrlIsNotSet() {
        MockEnvironment environment = new MockEnvironment().withProperty("sso.url", PUBLIC_URL);
        assertThrows(IllegalArgumentException.class, () -> resolveKeycloakUrl(environment));
    }
}
