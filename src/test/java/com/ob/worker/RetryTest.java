package com.ob.worker;

import com.ob.IntegrationTestBase;
import com.ob.model.OutboxEvent;
import com.ob.model.Transfer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryTest extends IntegrationTestBase {

    @Autowired
    EventProcessor processor;

    @Test
    void failedSendIsScheduledForLater() {
        when(publisher.publish(any())).thenReturn(false);
        Transfer transfer = newTransfer();

        processor.processOne("w1");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, attempts, last_error, available_at > now() AS in_future " +
                        "FROM outbox_events WHERE entity_id = ? AND event_type = ?",
                transfer.getId(), INITIATED);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isNotNull();
        assertThat(row.get("in_future")).isEqualTo(true);
    }

    @Test
    void attemptsGoUpOnEveryFailure() {
        when(publisher.publish(any())).thenReturn(false);
        Transfer transfer = newTransfer();

        for (int i = 0; i < 3; i++) {
            makeReady(transfer.getId());
            processor.processOne("w1");
        }

        assertThat(attemptsOf(transfer.getId(), INITIATED)).isEqualTo(3);
    }

    @Test
    void retryUsesTheSameEventId() {
        newTransfer();

        when(publisher.publish(any())).thenReturn(false);
        processor.processOne("w1");
        ArgumentCaptor<OutboxEvent> first = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(publisher).publish(first.capture());
        UUID firstId = first.getValue().getId();

        jdbc.update("UPDATE outbox_events SET available_at = now() WHERE id = ?", firstId);

        reset(publisher);
        when(publisher.publish(any())).thenReturn(true);
        processor.processOne("w1");
        ArgumentCaptor<OutboxEvent> second = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(publisher).publish(second.capture());

        assertThat(second.getValue().getId()).isEqualTo(firstId);
    }
}
