package com.ob.worker;

import com.ob.model.OutboxEvent;
import com.ob.publisher.EventPublisher;
import com.ob.service.ClaimService;
import com.ob.service.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    private final ClaimService claimService;
    private final EventPublisher eventPublisher;
    private final RetryPolicy retryPolicy;

    public EventProcessor(ClaimService claimService, EventPublisher eventPublisher, RetryPolicy retryPolicy) {
        this.claimService = claimService;
        this.eventPublisher = eventPublisher;
        this.retryPolicy = retryPolicy;
    }

    // false when nothing claimed
    public boolean processOne(String workerName) {
        OutboxEvent event = claimService.claimNext(workerName);
        if (event == null) {
            return false;
        }

        log.debug("{} claimed event {} ({})", workerName, event.getId(), event.getEventType());

        boolean delivered;
        try {
            delivered = eventPublisher.publish(event);
        } catch (Exception e) {
            // count as failure
            log.warn("Publisher threw for event {}: {}", event.getId(), e.getMessage());
            delivered = false;
        }

        if (delivered) {
            handleSuccess(event, workerName);
        } else {
            handleFailure(event, workerName);
        }
        return true;
    }

    private void handleSuccess(OutboxEvent event, String workerName) {
        if (claimService.markPublished(event)) {
            log.debug("{} published event {}", workerName, event.getId());
        } else {
            // reclaimer took it
            log.warn("{} lost claim on event {}, ignoring result", workerName, event.getId());
        }
    }

    private void handleFailure(OutboxEvent event, String workerName) {
        int attemptsUsed = event.getAttempts() + 1;
        String error = "delivery failed on attempt " + attemptsUsed;

        if (attemptsUsed >= event.getMaxAttempts()) {
            if (claimService.markDead(event, error)) {
                log.warn("Event {} moved to DEAD after {} attempts", event.getId(), attemptsUsed);
            } else {
                log.warn("{} lost claim on event {}, ignoring result", workerName, event.getId());
            }
            return;
        }

        long delay = retryPolicy.nextDelay(attemptsUsed);
        Instant nextTry = Instant.now().plusMillis(delay);

        if (claimService.markForRetry(event, nextTry, error)) {
            log.warn("Event {} failed, attempt {}, retry in {} ms", event.getId(), attemptsUsed, delay);
        } else {
            log.warn("{} lost claim on event {}, ignoring result", workerName, event.getId());
        }
    }
}
