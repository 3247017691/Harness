package io.harnessengineering.app;

import io.harnessengineering.session.SessionStore;
import io.harnessengineering.session.JsonlSessionStore;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring assembly over the framework-free core. Dependency direction is one-way:
 * this adapter depends on {@code harness-core}, which never depends on Spring.
 */
@Configuration
@EnableConfigurationProperties(HarnessProperties.class)
public class HarnessRuntimeConfiguration {

    @Bean
    public SessionStore sessionStore(HarnessProperties properties) {
        return new JsonlSessionStore(Path.of(properties.sessionDir()));
    }
}
