package io.harnessengineering.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.InMemorySessionStore;
import io.harnessengineering.session.SessionEventTypes;
import io.harnessengineering.session.SessionId;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HarnessHttpServerTest {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final InMemorySessionStore store = new InMemorySessionStore();
    private HarnessHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesEventsAndMessagesAsJson() throws Exception {
        server = startServer();
        SessionId id = new SessionId("demo");
        server.session(id).append(SessionEventTypes.USER_MESSAGE, JsonNodeFactory.instance.objectNode()
                .put("role", "user").put("content", "hello"));

        String events = get("/sessions/demo");
        assertTrue(events.contains("user/message"));
        assertTrue(events.contains("hello"));

        String messages = get("/sessions/demo/messages");
        assertTrue(messages.contains("\"role\":\"user\""));
        assertTrue(messages.contains("hello"));
    }

    @Test
    void sseReplaysThenStreamsCommittedEvents() throws Exception {
        server = startServer();
        SessionId id = new SessionId("live");
        EventLogSession session = server.session(id);
        session.append(SessionEventTypes.USER_MESSAGE, message("user", "before"));

        HttpRequest request = HttpRequest.newBuilder(uri("/sessions/live/stream")).GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("text/event-stream"));

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

        session.append(SessionEventTypes.ASSISTANT_MESSAGE, message("assistant", "live-answer"));

        reading.get(3, TimeUnit.SECONDS);
        readerExecutor.shutdownNow();
        String joined = String.join("\n", received);
        assertTrue(joined.contains("before"));
        assertTrue(joined.contains("live-answer"));
        assertTrue(joined.contains("event: session-event"));
    }

    @Test
    void servesBrowserClientPage() throws Exception {
        server = startServer();

        HttpResponse<String> page = client.send(HttpRequest.newBuilder(uri("/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("content-type").orElse("").startsWith("text/html"));
        assertTrue(page.body().contains("EventSource"));
        assertTrue(page.body().contains("Session Workbench"));
    }

    @Test
    void invalidIdsAndUnknownRoutesReturnErrors() throws Exception {
        server = startServer();

        assertEquals(400, getStatus("/sessions/"));
        assertEquals(404, getStatus("/sessions/demo/unknown"));
        assertEquals(400, getStatus("/sessions/%20"));
    }

    private HarnessHttpServer startServer() throws Exception {
        HarnessHttpServer started = new HarnessHttpServer(store, 0);
        started.start();
        return started;
    }

    private String get(String path) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private int getStatus(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode message(String role, String content) {
        return JsonNodeFactory.instance.objectNode().put("role", role).put("content", content);
    }
}
