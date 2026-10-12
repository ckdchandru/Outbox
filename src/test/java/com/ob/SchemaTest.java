package com.ob;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class SchemaTest extends IntegrationTestBase {

    private static final String INSERT_EVENT =
            "INSERT INTO outbox_events (id, entity_id, event_type, payload, status, max_attempts) " +
                    "VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)";

    @Test
    void validTransferCanBeInserted() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO transfers (id, amount, status) VALUES (?, ?, ?)",
                id, new BigDecimal("10.00"), "PENDING");

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM transfers WHERE id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void transferWithUnknownStatusIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO transfers (id, amount, status) VALUES (?, ?, ?)",
                UUID.randomUUID(), new BigDecimal("10.00"), "WRONG"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transferWithZeroAmountIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO transfers (id, amount, status) VALUES (?, ?, ?)",
                UUID.randomUUID(), BigDecimal.ZERO, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sequenceNumberIsFilledAndGrows() {
        UUID entityId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update(INSERT_EVENT, first, entityId, "A", "{}", "PENDING", 5);
        jdbc.update(INSERT_EVENT, second, entityId, "B", "{}", "PENDING", 5);

        Long firstSeq = jdbc.queryForObject("SELECT sequence_no FROM outbox_events WHERE id = ?", Long.class, first);
        Long secondSeq = jdbc.queryForObject("SELECT sequence_no FROM outbox_events WHERE id = ?", Long.class, second);

        assertThat(firstSeq).isNotNull();
        assertThat(secondSeq).isGreaterThan(firstSeq);
    }

    @Test
    void eventWithUnknownStatusIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_EVENT,
                UUID.randomUUID(), UUID.randomUUID(), "A", "{}", "LOST", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void attemptsCannotGoAboveMaxAttempts() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO outbox_events (id, entity_id, event_type, payload, status, attempts, max_attempts) " +
                        "VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "A", "{}", "PENDING", 6, 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void processingEventMustHaveClaimToken() {
        assertThatThrownBy(() -> jdbc.update(INSERT_EVENT,
                UUID.randomUUID(), UUID.randomUUID(), "A", "{}", "PROCESSING", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void publishedEventMustHavePublishedTime() {
        assertThatThrownBy(() -> jdbc.update(INSERT_EVENT,
                UUID.randomUUID(), UUID.randomUUID(), "A", "{}", "PUBLISHED", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
