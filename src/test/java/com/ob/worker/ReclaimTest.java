package com.ob.worker;

import com.ob.IntegrationTestBase;
import com.ob.model.OutboxEvent;
import com.ob.model.Transfer;
import com.ob.service.ClaimService;
import com.ob.service.ReclaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ReclaimTest extends IntegrationTestBase {

    @Autowired
    ReclaimService reclaimService;

    @Autowired
    ClaimService claimService;

    @Autowired
    EventProcessor processor;

    // like a crashed worker
    private void makeStuck(UUID entityId, String lockedAtSql) {
        jdbc.update("UPDATE outbox_events SET status = 'PROCESSING', locked_by = 'dead-worker', " +
                        "locked_at = " + lockedAtSql + ", claim_token = ? WHERE entity_id = ? AND event_type = ?",
                UUID.randomUUID(), entityId, INITIATED);
    }

    @Test
    void stuckEventGoesBackToPending() {
        Transfer transfer = newTransfer();
        makeStuck(transfer.getId(), "now() - interval '1 minute'");

        int count = reclaimService.reclaimStuckEvents();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, attempts, claim_token FROM outbox_events WHERE entity_id = ? AND event_type = ?",
                transfer.getId(), INITIATED);
        assertThat(count).isEqualTo(1);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("claim_token")).isNull();
    }

    @Test
    void reclaimedEventIsPublishedOnNextPoll() {
        when(publisher.publish(any())).thenReturn(true);
        Transfer transfer = newTransfer();
        makeStuck(transfer.getId(), "now() - interval '1 minute'");

        reclaimService.reclaimStuckEvents();
        makeReady(transfer.getId());
        processor.processOne("w1");

        assertThat(statusOf(transfer.getId(), INITIATED)).isEqualTo("PUBLISHED");
    }

    @Test
    void recentlyClaimedEventIsLeftAlone() {
        Transfer transfer = newTransfer();
        makeStuck(transfer.getId(), "now()");

        int count = reclaimService.reclaimStuckEvents();

        assertThat(count).isZero();
        assertThat(statusOf(transfer.getId(), INITIATED)).isEqualTo("PROCESSING");
    }

    @Test
    void slowWorkerCannotFinishReclaimedEvent() {
        Transfer transfer = newTransfer();
        OutboxEvent claimed = claimService.claimNext("slow-worker");
        jdbc.update("UPDATE outbox_events SET locked_at = now() - interval '1 minute' WHERE id = ?",
                claimed.getId());

        reclaimService.reclaimStuckEvents();
        boolean finished = claimService.markPublished(claimed);

        assertThat(finished).isFalse();
        assertThat(statusOf(transfer.getId(), INITIATED)).isEqualTo("PENDING");
    }
}
