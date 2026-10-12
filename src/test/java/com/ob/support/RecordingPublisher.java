package com.ob.support;

import com.ob.model.OutboxEvent;
import com.ob.publisher.EventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

// remembers every send
public class RecordingPublisher implements EventPublisher {

    public record Sent(UUID eventId, UUID entityId, String eventType, long order, Instant sentAt) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final AtomicLong counter = new AtomicLong();

    @Override
    public boolean publish(OutboxEvent event) {
        sent.add(new Sent(event.getId(), event.getEntityId(), event.getEventType(),
                counter.incrementAndGet(), Instant.now()));
        return true;
    }

    public List<Sent> getSent() {
        return sent;
    }

    public int count() {
        return sent.size();
    }
}
