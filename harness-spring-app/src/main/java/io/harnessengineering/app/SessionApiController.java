package io.harnessengineering.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.harnessengineering.core.Effect;
import io.harnessengineering.projection.SessionProjection;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.Message;
import io.harnessengineering.session.SessionEvent;
import io.harnessengineering.session.SessionId;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Read-mostly Spring MVC API over Session state. Sending a message is the one
 * mutation: it hands the user input to the Session's Agent, which appends the
 * turn events back through the shared Session log (and thus the SSE stream).
 */
@RestController
public class SessionApiController {
    private final SessionRegistry sessions;
    private final AgentHost agents;
    private final SessionProjection projection = new SessionProjection();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    public SessionApiController(SessionRegistry sessions, AgentHost agents) {
        this.sessions = sessions;
        this.agents = agents;
    }

    @GetMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SessionSummary> sessions() {
        return sessions.list().stream()
                .map(id -> {
                    List<SessionEvent> events = sessions.session(id).events();
                    return new SessionSummary(id.value(), events.size(),
                            events.isEmpty() ? null : events.getLast().time());
                })
                .toList();
    }

    @PostMapping(value = "/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> create() {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", sessions.create().value()));
    }

    @GetMapping(value = "/sessions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SessionEvent> events(@PathVariable String id) {
        return sessions.session(new SessionId(id)).events();
    }

    @GetMapping(value = "/sessions/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Message> messages(@PathVariable String id) {
        return sessions.session(new SessionId(id)).deriveMessages();
    }

    @GetMapping(value = "/sessions/{id}/projection", produces = MediaType.APPLICATION_JSON_VALUE)
    public SessionProjection.Result projection(@PathVariable String id) {
        return projection.project(sessions.session(new SessionId(id)).events());
    }

    /** Sends a user message; the Agent turn streams back over the session SSE stream. */
    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> send(@PathVariable String id, @RequestBody SendMessageRequest body) {
        String content = body.content() == null ? "" : body.content().strip();
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content must not be blank"));
        }
        SessionId sessionId = new SessionId(id);
        agents.agent(sessionId).inbox().followup(new Message("user", content));
        return ResponseEntity.accepted().body(Map.of("status", "queued"));
    }

    @GetMapping("/sessions/{id}/stream")
    public SseEmitter stream(@PathVariable String id) {
        EventLogSession session = sessions.session(new SessionId(id));
        SseEmitter emitter = new SseEmitter(0L);
        Effect subscription = session.onEvent(event -> {
            try {
                emitter.send(SseEmitter.event().name("session-event").data(event));
            } catch (Exception ignored) {
                // Subscriber is gone; completion callbacks release the subscription.
            }
        });
        emitter.onCompletion(subscription::close);
        emitter.onTimeout(() -> {
            subscription.close();
            emitter.complete();
        });
        Thread.ofVirtual().start(() -> {
            try {
                for (SessionEvent event : session.events()) {
                    emitter.send(SseEmitter.event().name("session-event").data(event));
                }
            } catch (Exception exception) {
                subscription.close();
            }
        });
        return emitter;
    }

    @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> page() throws IOException {
        try (InputStream resource = getClass().getResourceAsStream("/web/index.html")) {
            if (resource == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok().body(resource.readAllBytes());
        }
    }

    /** Session listing row for the browser sidebar. */
    public record SessionSummary(String id, long events, java.time.Instant lastEventAt) { }

    /** Body of the send-message endpoint. */
    public record SendMessageRequest(String content) { }
}