package io.harnessengineering.app;

import io.harnessengineering.agent.Agent;
import io.harnessengineering.http.HarnessHttpServer;
import io.harnessengineering.session.Message;
import io.harnessengineering.session.SessionId;
import io.harnessengineering.session.SessionStore;
import io.harnessengineering.session.JsonlSessionStore;
import io.harnessengineering.tools.ToolPipeline;
import io.harnessengineering.tools.ToolRegistry;
import java.io.IOException;
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

    @Bean(destroyMethod = "close")
    public HarnessHttpServer httpServer(HarnessProperties properties, SessionStore sessionStore) throws IOException {
        HarnessHttpServer server = new HarnessHttpServer(sessionStore, properties.httpPort());
        server.start();
        return server;
    }

    @Bean(destroyMethod = "close")
    public Agent demoAgent(HarnessHttpServer httpServer) {
        Agent agent = new Agent(
                httpServer.session(new SessionId("demo")),
                new EchoProvider(),
                "demo-model",
                new ToolPipeline(new ToolRegistry()));
        agent.start();
        agent.inbox().followup(new Message("user", "hello from spring"));
        return agent;
    }
}
