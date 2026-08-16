package io.harnessengineering.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.harnessengineering.http.HarnessHttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class HarnessRuntimeConfigurationTest {
    @TempDir
    Path directory;

    @Test
    void assemblesStoreAndServesSessionState() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "harness.session-dir", directory.toString(),
                "harness.http-port", "0")));
        context.register(HarnessRuntimeConfiguration.class);
        context.refresh();
        try {
            HarnessHttpServer server = context.getBean(HarnessHttpServer.class);
            assertTrue(server.port() > 0);
            assertTrue(context.containsBean("demoAgent"));

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> events = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + server.port() + "/sessions/demo")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, events.statusCode());
            assertTrue(events.body().contains("user/message"));

            HttpResponse<String> page = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + server.port() + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("EventSource"));
        } finally {
            context.close();
        }
    }
}
