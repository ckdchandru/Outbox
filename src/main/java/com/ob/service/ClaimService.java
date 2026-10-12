package com.ob.service;

import com.ob.model.EventStatus;
import com.ob.model.OutboxEvent;
import com.ob.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClaimService {

    private final OutboxEventRepository outboxEventRepository;

    public ClaimService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    // returns null if empty
    @Transactional
    public OutboxEvent claimNext(String workerName) {
        Optional<OutboxEvent> next = outboxEventRepository.findNextEventToPublish();
        if (next.isEmpty()) {
            return null;
        }

        OutboxEvent event = next.get();
        event.setStatus(EventStatus.PROCESSING);
        event.setClaimToken(UUID.randomUUID());
        event.setLockedBy(workerName);
        event.setLockedAt(Instant.now());
        return event;
    }

    public boolean markPublished(OutboxEvent event) {
        int rows = outboxEventRepository.markPublished(event.getId(), event.getClaimToken());
        return rows == 1;
    }

    public boolean markForRetry(OutboxEvent event, Instant nextTry, String error) {
        int rows = outboxEventRepository.markForRetry(event.getId(), event.getClaimToken(), nextTry, error);
        return rows == 1;
    }

    public boolean markDead(OutboxEvent event, String error) {
        int rows = outboxEventRepository.markDead(event.getId(), event.getClaimToken(), error);
        return rows == 1;
    }
}
