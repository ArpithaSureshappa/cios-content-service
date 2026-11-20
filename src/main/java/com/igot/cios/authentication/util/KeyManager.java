package com.igot.cios.authentication.util;

import com.igot.cios.authentication.model.KeyData;
import com.igot.cios.util.CbServerProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the Keycloak realm public keys from the directory {@code accesstoken.publickey.basepath}.
 * Every regular file is one PEM public key and its file name is the key id ({@code kid}) used in token headers.
 */
@Slf4j
@Component
public class KeyManager {

    private final Map<String, KeyData> keyMap = new ConcurrentHashMap<>();

    private final CbServerProperties cbServerProperties;

    @Autowired
    public KeyManager(CbServerProperties cbServerProperties) {
        this.cbServerProperties = cbServerProperties;
    }

    @PostConstruct
    public void init() {
        String basePath = cbServerProperties.getAccessTokenPublicKeyBasePath();
        if (StringUtils.isBlank(basePath)) {
            // A blank path would make Paths.get("") scan the whole working directory.
            log.error("KeyManager::init: publickey.basepath is not configured, no public keys loaded");
            return;
        }
        try (Stream<Path> walk = Files.walk(Paths.get(basePath))) {
            List<Path> files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path file : files) {
                try {
                    String content = String.join("", Files.readAllLines(file, StandardCharsets.UTF_8));
                    String keyId = file.getFileName().toString();
                    keyMap.put(keyId, new KeyData(keyId, loadPublicKey(content)));
                } catch (Exception e) {
                    log.error("KeyManager::init: unable to read public key file {}", file.getFileName(), e);
                }
            }
            log.info("KeyManager::init: loaded {} public key(s) from {}", keyMap.size(), basePath);
        } catch (Exception e) {
            log.error("KeyManager::init: unable to load public keys from {}", basePath, e);
        }
    }

    public KeyData getPublicKey(String keyId) {
        return keyMap.get(keyId);
    }

    static PublicKey loadPublicKey(String pem) throws Exception {
        String stripped = pem.replaceAll("-+BEGIN PUBLIC KEY-+", "")
                .replaceAll("-+END PUBLIC KEY-+", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }
}
