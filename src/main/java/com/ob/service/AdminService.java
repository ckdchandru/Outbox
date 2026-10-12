package com.ob.service;

import com.ob.dto.EventSummary;
import com.ob.dto.PageResponse;
import com.ob.exception.EventNotDeadException;
import com.ob.exception.NotFoundException;
import com.ob.model.EventStatus;
import com.ob.model.OutboxEvent;
import com.ob.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;

    public AdminService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummary> getDeadEvents(int page, int size) {
        if (page < 0) {
            page = 0;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            size = 20;
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OutboxEvent> result = outboxEventRepository.findByStatus(EventStatus.DEAD, pageRequest);

        List<EventSummary> content = result.getContent().stream()
                .map(EventSummary::from)
                .toList();

        return new PageResponse<>(content, page, size, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OutboxEvent getEvent(UUID id) {
        return outboxEventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("outbox event not found: " + id));
    }

    @Transactional
    public void requeue(UUID id) {
        // lock the row
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("outbox event not found: " + id));

        if (event.getStatus() != EventStatus.DEAD) {
            throw new EventNotDeadException(id, event.getStatus().name());
        }

        event.setStatus(EventStatus.PENDING);
        event.setAttempts(0);
        event.setAvailableAt(Instant.now());
        event.setClaimToken(null);
        event.setLockedBy(null);
        event.setLockedAt(null);
        event.setLastError(null);

        log.info("Event {} requeued by admin", id);
    }
}
