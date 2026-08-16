package io.harnessengineering.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.harnessengineering.core.Effect;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.SessionEvent;
import io.harnessengineering.session.SessionId;
import io.harnessengineering.session.SessionStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only HTTP API over Session state. Sessions are served from a shared in-JVM
 * view so events appended by an Agent reach SSE subscribers immediately. The web
 * layer never mutates loop internals directly.
 *
 * <ul>
 *   <li>GET / - browser client page (EventSource + fetch, read-only)</li>
 *   <li>GET /sessions/{id} - committed events as JSON</li>
 *   <li>GET /sessions/{id}/messages - derived model messages as JSON</li>
 *   <li>GET /sessions/{id}/stream - SSE stream that replays then follows</li>
 * </ul>
 */
public final class HarnessHttpServer implements AutoCloseable {
    private static final String JSON_TYPE = "application/json; charset=utf-8";
    private static final String SSE_TYPE = "text/event-stream; charset=utf-8";
    private static final long HEARTBEAT_MILLIS = 15_000;

    private final SessionStore store;
    private final HttpServer server;
    private final ExecutorService executor;
    private final ObjectMapper json;
    private final Map<SessionId, EventLogSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a server bound to a port.
     *
     * @param store session event backend
     * @param port listening port, or 0 for an ephemeral port
     * @throws IOException when the server socket cannot be created
     */
    public HarnessHttpServer(SessionStore store, int port) throws IOException {
        this.store = Objects.requireNonNull(store, "store");
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(executor);
        this.server.createContext("/sessions", exchange -> handle(exchange));
        this.server.createContext("/", this::handleStatic);
    }

    /** Starts accepting connections. */
    public void start() {
        server.start();
    }

    /** @return the port this server is listening on */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Returns the shared session view for an ID, creating it by replaying the store
     * when needed. Append to this instance to have SSE subscribers receive events.
     */
    public EventLogSession session(SessionId id) {
        Objects.requireNonNull(id, "id");
        return sessions.computeIfAbsent(id, ignored -> new EventLogSession(id, store));
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        try {
            if (!exchange.getRequestMethod().equals("GET")) {
                respond(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            String rest = path.substring("/sessions".length());
            if (rest.isEmpty() || rest.equals("/")) {
                respond(exchange, 400, "{\"error\":\"session id required\"}");
                return;
            }
            String[] parts = rest.substring(1).split("/", 2);
            EventLogSession session = session(new SessionId(parts[0]));
            String subpath = parts.length > 1 ? parts[1] : "";
            switch (subpath) {
                case "" -> respond(exchange, 200, json.writeValueAsString(session.events()));
                case "messages" -> respond(exchange, 200, json.writeValueAsString(session.deriveMessages()));
                case "stream" -> stream(exchange, session);
                default -> respond(exchange, 404, "{\"error\":\"not found\"}");
            }
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, "{\"error\":\"invalid session id\"}");
        } catch (IOException exception) {
            respond(exchange, 500, "{\"error\":\"server error\"}");
        } finally {
            exchange.close();
        }
    }

    private void stream(HttpExchange exchange, EventLogSession session) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", SSE_TYPE);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        OutputStream output = exchange.getResponseBody();
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        Effect subscription = session.onEvent(event -> {
            try {
                synchronized (output) {
                    output.write(encode("event: session-event\ndata: " + json.writeValueAsString(event) + "\n\n"));
                    output.flush();
                }
            } catch (IOException exception) {
                closed.set(true);
            }
        });
        try {
            for (SessionEvent event : session.events()) {
                synchronized (output) {
                    output.write(encode("event: session-event\ndata: " + json.writeValueAsString(event) + "\n\n"));
                    output.flush();
                }
            }
            while (!closed.get()) {
                try {
                    Thread.sleep(HEARTBEAT_MILLIS);
                } catch (InterruptedException exception) {
                    break;
                }
                if (closed.get()) {
                    break;
                }
                try {
                    synchronized (output) {
                        output.write(encode(": keepalive\n\n"));
                        output.flush();
                    }
                } catch (IOException exception) {
                    break;
                }
            }
        } finally {
            subscription.close();
            exchange.close();
        }
    }

    private void handleStatic(HttpExchange exchange) {
        try {
            if (!exchange.getRequestMethod().equals("GET")) {
                respond(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!path.equals("/") && !path.equals("/index.html")) {
                respond(exchange, 404, "{\"error\":\"not found\"}");
                return;
            }
            try (InputStream resource = getClass().getResourceAsStream("/web/index.html")) {
                if (resource == null) {
                    respond(exchange, 500, "{\"error\":\"page missing\"}");
                    return;
                }
                byte[] bytes = resource.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
            }
        } catch (IOException exception) {
            respond(exchange, 500, "{\"error\":\"server error\"}");
        } finally {
            exchange.close();
        }
    }

    private void respond(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", JSON_TYPE);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (IOException exception) {
            // The connection is already unusable; nothing further can be sent.
        }
    }

    private static byte[] encode(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
