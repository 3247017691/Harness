package io.harnessengineering.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

class HarnessApplicationTest {
    @TempDir
    Path directory;

    @Test
    void bootsTomcatAndServesSessionState() throws Exception {
        SpringApplication application = new SpringApplication(HarnessApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setDefaultProperties(Map.of(
                "harness.session-dir", directory.toString(),
                "server.port", "0",
                "server.shutdown", "immediate"));
        try (ConfigurableApplicationContext context = application.run()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            String base = "http://127.0.0.1:" + port;

            HttpResponse<String> events = client.send(HttpRequest.newBuilder(
                            URI.create(base + "/sessions/demo")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, events.statusCode());
            assertTrue(events.body().contains("user/message"));

            HttpResponse<String> messages = client.send(HttpRequest.newBuilder(
                            URI.create(base + "/sessions/demo/messages")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(messages.body().contains("hello from spring"));

            HttpResponse<String> page = client.send(HttpRequest.newBuilder(
                            URI.create(base + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("EventSource"));
        }
    }

    @Test
    void streamsEventsOverSse() throws Exception {
        SpringApplication application = new SpringApplication(HarnessApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setDefaultProperties(Map.of(
                "harness.session-dir", directory.toString(),
                "server.port", "0",
                "server.shutdown", "immediate"));
        try (ConfigurableApplicationContext context = application.run()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/sessions/demo/stream")).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, response.statusCode());

            java.util.List<String> received = new java.util.ArrayList<>();
            var readerExecutor = Executors.newVirtualThreadPerTaskExecutor();
            CompletableFuture<Void> reading = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        received.add(line);
                        if (line.contains("live-answer")) {
                            return;
                        }
                    }
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }, readerExecutor);

            context.getBean(SessionRegistry.class)
                    .session(new io.harnessengineering.session.SessionId("demo"))
                    .append(io.harnessengineering.session.SessionEventTypes.ASSISTANT_MESSAGE,
                            JsonNodeFactory.instance.objectNode().put("role", "assistant").put("content", "live-answer"));

            reading.get(3, TimeUnit.SECONDS);
            readerExecutor.shutdownNow();
            String joined = String.join("\n", received);
            assertTrue(joined.contains("live-answer"), "missing live-answer; received:\n" + joined);
            assertTrue(joined.contains("event:session-event"), "missing sse marker; received:\n" + joined);
        }
    }
}
