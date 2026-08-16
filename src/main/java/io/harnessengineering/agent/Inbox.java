package io.harnessengineering.agent;

import io.harnessengineering.session.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Separates next-turn inputs from inputs injected into the current tool loop. */
public final class Inbox {
    private final BlockingQueue<Message> nextTurn = new LinkedBlockingQueue<>();
    private final List<Message> nextStep = new ArrayList<>();
    private boolean closed;

    /** Adds an input that opens or extends a future turn and wakes the agent. */
    public synchronized void followup(Message message) {
        ensureOpen();
        nextTurn.add(Objects.requireNonNull(message, "message"));
    }

    /** Adds input for the current turn's next step. */
    public synchronized void steer(Message message) {
        ensureOpen();
        nextStep.add(Objects.requireNonNull(message, "message"));
    }

    /** Adds input for the current turn's next step without waking an idle agent. */
    public synchronized void inject(Message message) {
        ensureOpen();
        nextStep.add(Objects.requireNonNull(message, "message"));
    }

    /** Claims one turn message, waiting until one arrives or the inbox closes. */
    public Message claimNextTurn() throws InterruptedException {
        while (true) {
            synchronized (this) {
                if (closed && nextTurn.isEmpty()) {
                    return null;
                }
            }
            Message message = nextTurn.poll(100, TimeUnit.MILLISECONDS);
            if (message != null) {
                return message;
            }
        }
    }

    /** Atomically claims all pending current-turn step inputs. */
    public synchronized List<Message> claimNextStep() {
        List<Message> messages = List.copyOf(nextStep);
        nextStep.clear();
        return messages;
    }

    public synchronized void close() {
        closed = true;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("inbox is closed");
        }
    }
}
