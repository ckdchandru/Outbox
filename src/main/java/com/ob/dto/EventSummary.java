package com.ob.dto;

import com.ob.model.OutboxEvent;

import java.time.Instant;
import java.util.UUID;

public record EventSummary(UUID id, UUID entityId, String eventType, int attempts,
                           String lastError, Instant createdAt) {

    public static EventSummary from(OutboxEvent event) {
        return new EventSummary(
                event.getId(),
                event.getEntityId(),
                event.getEventType(),
                event.getAttempts(),
                event.getLastError(),
                event.getCreatedAt());
    }
}
