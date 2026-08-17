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
        try (ConfigurableApplicationContext context = application.run(arguments())) {
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
        try (ConfigurableApplicationContext context = application.run(arguments())) {
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

    @Test
    void listsCreatesAndSendsToSessions() throws Exception {
        SpringApplication application = new SpringApplication(HarnessApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        try (ConfigurableApplicationContext context = application.run(arguments())) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            String base = "http://127.0.0.1:" + port;

            HttpResponse<String> listed = client.send(HttpRequest.newBuilder(
                            URI.create(base + "/sessions")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listed.statusCode());
            assertTrue(listed.body().contains("\"id\":\"demo\""));

            HttpResponse<String> created = client.send(HttpRequest.newBuilder(
                            URI.create(base + "/sessions")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(201, created.statusCode());
            String createdId = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(created.body()).path("id").asText();
            assertTrue(createdId.length() > 8);

            HttpResponse<String> sent = client.send(HttpRequest.newBuilder(
                            URI.create(base + "/sessions/" + createdId + "/messages"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"what time is it\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(202, sent.statusCode());

            // The per-session agent runs the turn asynchronously; poll for the tool round-trip.
            String eventsBody = "";
            for (int attempt = 0; attempt < 20; attempt++) {
                eventsBody = client.send(HttpRequest.newBuilder(
                                URI.create(base + "/sessions/" + createdId)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).body();
                if (eventsBody.contains("\"turn/end\"")) {
                    break;
                }
                Thread.sleep(100);
            }
            assertTrue(eventsBody.contains("\"tool/call\""), "missing tool/call; got: " + eventsBody);
            assertTrue(eventsBody.contains("\"tool/result\""), "missing tool/result; got: " + eventsBody);
            assertTrue(eventsBody.contains("\"harness_current_time\""), "missing time tool name; got: " + eventsBody);

            HttpResponse<String> projection = client.send(HttpRequest.newBuilder(
                            URI.create(base + "/sessions/" + createdId + "/projection")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, projection.statusCode());
            assertTrue(projection.body().contains("contextWindow"));
            assertTrue(projection.body().contains("systemTokens"));
            assertTrue(projection.body().contains("outputTokens"));
        }
    }

    private String[] arguments() {
        return new String[] {
                "--harness.session-dir=" + directory,
                "--server.port=0",
                "--server.shutdown=immediate"
        };
    }
}
