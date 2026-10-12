package com.ob.dto;

import com.ob.model.OutboxEvent;

import java.time.Instant;
import java.util.UUID;

public record EventDetails(
        UUID id,
        UUID entityId,
        String eventType,
        String status,
        String payload,
        Long sequenceNo,
        int attempts,
        int maxAttempts,
        Instant availableAt,
        String lockedBy,
        String lastError,
        Instant createdAt,
        Instant publishedAt) {

    public static EventDetails from(OutboxEvent event) {
        return new EventDetails(
                event.getId(),
                event.getEntityId(),
                event.getEventType(),
                event.getStatus().name(),
                event.getPayload(),
                event.getSequenceNo(),
                event.getAttempts(),
                event.getMaxAttempts(),
                event.getAvailableAt(),
                event.getLockedBy(),
                event.getLastError(),
                event.getCreatedAt(),
                event.getPublishedAt());
    }
}
