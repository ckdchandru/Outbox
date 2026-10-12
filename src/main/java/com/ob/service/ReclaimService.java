package com.ob.service;

import com.ob.model.EventStatus;
import com.ob.model.OutboxEvent;
import com.ob.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReclaimService {

    private static final Logger log = LoggerFactory.getLogger(ReclaimService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RetryPolicy retryPolicy;
    private final long timeoutMs;
    private final int batchSize;

    public ReclaimService(OutboxEventRepository outboxEventRepository,
                          RetryPolicy retryPolicy,
                          @Value("${outbox.reclaim.timeout-ms:300000}") long timeoutMs,
                          @Value("${outbox.reclaim.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.retryPolicy = retryPolicy;
        this.timeoutMs = timeoutMs;
        this.batchSize = batchSize;
    }

    // rescue events from crashed workers
    @Transactional
    public int reclaimStuckEvents() {
        Instant cutoff = Instant.now().minusMillis(timeoutMs);
        List<OutboxEvent> stuckEvents = outboxEventRepository.findStuckEvents(cutoff, batchSize);

        for (OutboxEvent event : stuckEvents) {
            int attemptsUsed = event.getAttempts() + 1;

            event.setAttempts(attemptsUsed);
            event.setClaimToken(null);
            event.setLockedBy(null);
            event.setLockedAt(null);
            event.setLastError("worker did not finish in time");

            if (attemptsUsed >= event.getMaxAttempts()) {
                event.setStatus(EventStatus.DEAD);
                log.warn("Stuck event {} moved to DEAD after {} attempts", event.getId(), attemptsUsed);
            } else {
                long delay = retryPolicy.nextDelay(attemptsUsed);
                event.setStatus(EventStatus.PENDING);
                event.setAvailableAt(Instant.now().plusMillis(delay));
                log.warn("Stuck event {} reclaimed, retry in {} ms", event.getId(), delay);
            }
        }

        return stuckEvents.size();
    }
}
