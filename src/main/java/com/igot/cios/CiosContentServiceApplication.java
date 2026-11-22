package com.igot.cios;

import org.igot.common.CommonAutoConfiguration;
import org.igot.common.PropertiesCache;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.auth.KeyManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Only the token validation part of common-util is wired in. Its auto-configuration is excluded because it would
 * scan every bean of the library, including Cassandra beans that clash with this service's own.
 */
@SpringBootApplication(exclude = CommonAutoConfiguration.class)
@Import({AccessTokenValidator.class, KeyManager.class, PropertiesCache.class})
@EnableScheduling
public class CiosContentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CiosContentServiceApplication.class, args);
	}

}
