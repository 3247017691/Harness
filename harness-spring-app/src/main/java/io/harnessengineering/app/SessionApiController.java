package io.harnessengineering.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.harnessengineering.core.Effect;
import io.harnessengineering.session.EventLogSession;
import io.harnessengineering.session.Message;
import io.harnessengineering.session.SessionEvent;
import io.harnessengineering.session.SessionId;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Read-only Spring MVC API over Session state. The web layer never mutates Agent
 * loop internals directly.
 */
@RestController
public class SessionApiController {
    private final SessionRegistry sessions;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    public SessionApiController(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @GetMapping(value = "/sessions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SessionEvent> events(@PathVariable String id) {
        return sessions.session(new SessionId(id)).events();
    }

    @GetMapping(value = "/sessions/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Message> messages(@PathVariable String id) {
        return sessions.session(new SessionId(id)).deriveMessages();
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
}
