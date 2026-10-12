package com.ob.worker;

import com.ob.IntegrationTestBase;
import com.ob.model.OutboxEvent;
import com.ob.model.Transfer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventProcessorTest extends IntegrationTestBase {

    @Autowired
    EventProcessor processor;

    @BeforeEach
    void publisherWorks() {
        when(publisher.publish(any())).thenReturn(true);
    }

    @Test
    void completedWaitsUntilInitiatedIsPublished() {
        Transfer transfer = newTransfer();

        processor.processOne("w1");
        assertThat(statusOf(transfer.getId(), INITIATED)).isEqualTo("PUBLISHED");
        assertThat(statusOf(transfer.getId(), COMPLETED)).isEqualTo("PENDING");

        processor.processOne("w1");
        assertThat(statusOf(transfer.getId(), COMPLETED)).isEqualTo("PUBLISHED");
    }

    @Test
    void eventsAreSentInTheOrderTheyWereWritten() {
        newTransfer();

        processor.processOne("w1");
        processor.processOne("w1");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(publisher, times(2)).publish(captor.capture());

        List<OutboxEvent> sent = captor.getAllValues();
        assertThat(sent.get(0).getEventType()).isEqualTo(INITIATED);
        assertThat(sent.get(1).getEventType()).isEqualTo(COMPLETED);
    }

    @Test
    void publishedRowHasTimeAndNoToken() {
        Transfer transfer = newTransfer();

        processor.processOne("w1");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT published_at, claim_token, locked_by FROM outbox_events WHERE entity_id = ? AND event_type = ?",
                transfer.getId(), INITIATED);
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("claim_token")).isNull();
        assertThat(row.get("locked_by")).isNull();
    }

    @Test
    void publisherGetsTheSameIdAsDatabaseRow() {
        Transfer transfer = newTransfer();

        processor.processOne("w1");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(eventIdOf(transfer.getId(), INITIATED));
    }

    @Test
    void returnsFalseWhenThereIsNothingToDo() {
        boolean didWork = processor.processOne("w1");

        assertThat(didWork).isFalse();
        verify(publisher, never()).publish(any());
    }
}
