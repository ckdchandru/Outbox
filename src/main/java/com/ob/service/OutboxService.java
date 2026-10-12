package com.ob.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ob.model.EventStatus;
import com.ob.model.OutboxEvent;
import com.ob.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public OutboxService(OutboxEventRepository outboxEventRepository,
                         ObjectMapper objectMapper,
                         @Value("${outbox.retry.max-attempts:5}") int maxAttempts) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    // joins caller's transaction
    public void saveEvent(UUID entityId, String eventType, Object data) {
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("could not convert event data to json", e);
        }

        Instant now = Instant.now();

        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEntityId(entityId);
        event.setEventType(eventType);
        event.setPayload(json);
        event.setStatus(EventStatus.PENDING);
        event.setAttempts(0);
        event.setMaxAttempts(maxAttempts);
        event.setAvailableAt(now);
        event.setCreatedAt(now);

        outboxEventRepository.save(event);
    }
}
