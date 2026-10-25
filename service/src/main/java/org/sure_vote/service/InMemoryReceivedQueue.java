package org.sure_vote.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stands in for the SQS queue in tests, development, and the showcase. Received messages stay in flight until
 * they are deleted or released, as on SQS.
 */
public final class InMemoryReceivedQueue implements ReceivedQueue {
    private final Object lock = new Object();
    private final List<UUID> waiting = new ArrayList<>();
    private final Map<String, UUID> inFlight = new LinkedHashMap<>();

    @Override
    public void send(final UUID issuanceId) {
        synchronized (this.lock) {
            this.waiting.add(issuanceId);
        }
    }

    @Override
    public List<QueuedMark> receive(final int max) {
        synchronized (this.lock) {
            return this.taken(max);
        }
    }

    @Override
    public void delete(final List<QueuedMark> marks) {
        synchronized (this.lock) {
            for (final var mark : marks) {
                this.inFlight.remove(mark.handle());
            }
        }
    }

    @Override
    public void release(final List<QueuedMark> marks) {
        synchronized (this.lock) {
            for (final var mark : marks) {
                this.returned(mark);
            }
        }
    }

    public int size() {
        synchronized (this.lock) {
            return this.waiting.size() + this.inFlight.size();
        }
    }

    private List<QueuedMark> taken(final int max) {
        final var marks = new ArrayList<QueuedMark>();
        while (!this.waiting.isEmpty() && marks.size() < max) {
            final var id = this.waiting.removeFirst();
            final var handle = UUID.randomUUID();
            final var text = handle.toString();
            this.inFlight.put(text, id);
            marks.add(new QueuedMark(id, text));
        }
        return marks;
    }

    private void returned(final QueuedMark mark) {
        final var id = this.inFlight.remove(mark.handle());
        if (id != null) {
            this.waiting.add(id);
        }
    }
}
