package com.ob.transfer;

import com.ob.IntegrationTestBase;
import com.ob.model.Transfer;
import com.ob.service.OutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

class TransferServiceTest extends IntegrationTestBase {

    @SpyBean
    OutboxService outboxService;

    @Test
    void transferAndBothEventsAreSavedTogether() {
        Transfer transfer = newTransfer();

        String transferStatus = jdbc.queryForObject(
                "SELECT status FROM transfers WHERE id = ?", String.class, transfer.getId());
        Integer eventCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE entity_id = ?", Integer.class, transfer.getId());

        assertThat(transferStatus).isEqualTo("COMPLETED");
        assertThat(eventCount).isEqualTo(2);
    }

    @Test
    void nothingIsSavedWhenSecondEventFails() {
        doThrow(new RuntimeException("boom"))
                .when(outboxService).saveEvent(any(), eq(COMPLETED), any());

        assertThatThrownBy(() -> transferService.createTransfer(new BigDecimal("99.00")))
                .isInstanceOf(RuntimeException.class);

        Integer transfers = jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Integer.class);
        Integer events = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
        assertThat(transfers).isZero();
        assertThat(events).isZero();
    }

    @Test
    void completedEventGetsHigherSequenceThanInitiated() {
        Transfer transfer = newTransfer();

        Long initiatedSeq = jdbc.queryForObject(
                "SELECT sequence_no FROM outbox_events WHERE entity_id = ? AND event_type = ?",
                Long.class, transfer.getId(), INITIATED);
        Long completedSeq = jdbc.queryForObject(
                "SELECT sequence_no FROM outbox_events WHERE entity_id = ? AND event_type = ?",
                Long.class, transfer.getId(), COMPLETED);

        assertThat(completedSeq).isGreaterThan(initiatedSeq);
    }
}
