package com.ob.repository;

import com.ob.model.EventStatus;
import com.ob.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // oldest ready event per entity
    @Query(value = """
            SELECT e.* FROM outbox_events e
            WHERE e.status = 'PENDING'
              AND e.available_at <= now()
              AND NOT EXISTS (
                  SELECT 1 FROM outbox_events older
                  WHERE older.entity_id = e.entity_id
                    AND older.sequence_no < e.sequence_no
                    AND older.status IN ('PENDING', 'PROCESSING')
              )
            ORDER BY e.available_at, e.sequence_no
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEvent> findNextEventToPublish();

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PROCESSING'
              AND locked_at < :cutoff
            ORDER BY locked_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> findStuckEvents(@Param("cutoff") Instant cutoff, @Param("limit") int limit);

    Page<OutboxEvent> findByStatus(EventStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") UUID id);

    // token must still match
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE outbox_events
            SET status = 'PUBLISHED', published_at = now(), claim_token = NULL,
                locked_by = NULL, locked_at = NULL, last_error = NULL
            WHERE id = :id AND claim_token = :token
            """, nativeQuery = true)
    int markPublished(@Param("id") UUID id, @Param("token") UUID token);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE outbox_events
            SET status = 'PENDING', attempts = attempts + 1, available_at = :nextTry,
                claim_token = NULL, locked_by = NULL, locked_at = NULL, last_error = :error
            WHERE id = :id AND claim_token = :token
            """, nativeQuery = true)
    int markForRetry(@Param("id") UUID id, @Param("token") UUID token,
                     @Param("nextTry") Instant nextTry, @Param("error") String error);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE outbox_events
            SET status = 'DEAD', attempts = attempts + 1,
                claim_token = NULL, locked_by = NULL, locked_at = NULL, last_error = :error
            WHERE id = :id AND claim_token = :token
            """, nativeQuery = true)
    int markDead(@Param("id") UUID id, @Param("token") UUID token, @Param("error") String error);
}
