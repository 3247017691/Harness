package io.harnessengineering.app;

import io.harnessengineering.agent.Agent;
import io.harnessengineering.session.Message;
import io.harnessengineering.session.SessionId;
import io.harnessengineering.tools.ToolPipeline;
import io.harnessengineering.tools.ToolRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Lazily creates and owns one {@link Agent} per Session, wiring the demo provider
 * plus the demo tool roster. Sessions that were never sent a message hold no agent.
 */
@Component
public final class AgentHost implements AutoCloseable {
    private final SessionRegistry sessions;
    private final DemoProvider provider = new DemoProvider();
    private final Map<SessionId, Agent> agents = new ConcurrentHashMap<>();

    public AgentHost(SessionRegistry sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /** Agent for a Session, created and started on first use. */
    public Agent agent(SessionId id) {
        Objects.requireNonNull(id, "id");
        return agents.computeIfAbsent(id, sid -> {
            ToolRegistry tools = new ToolRegistry();
            tools.register(new CurrentTimeTool());
            Agent agent = new Agent(sessions.session(sid), provider, DemoProvider.MODEL,
                    new ToolPipeline(tools));
            agent.start();
            return agent;
        });
    }

    @PostConstruct
    void bootstrapDemo() {
        agent(new SessionId("demo")).inbox().followup(new Message("user", "hello from spring"));
    }

    @Override
    @PreDestroy
    public void close() {
        agents.values().forEach(Agent::close);
        agents.clear();
    }
}