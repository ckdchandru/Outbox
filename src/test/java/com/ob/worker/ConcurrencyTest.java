package com.ob.worker;

import com.ob.IntegrationTestBase;
import com.ob.model.Transfer;
import com.ob.service.ClaimService;
import com.ob.service.RetryPolicy;
import com.ob.support.RecordingPublisher;
import com.ob.support.TestWorkers;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyTest extends IntegrationTestBase {

    private static final int THREADS = 10;
    private static final int TRANSFERS = 20;

    @Autowired
    ClaimService claimService;

    @Autowired
    RetryPolicy retryPolicy;

    private RecordingPublisher runWorkers(int expectedEvents) throws InterruptedException {
        RecordingPublisher recorder = new RecordingPublisher();
        EventProcessor processor = new EventProcessor(claimService, recorder, retryPolicy);
        TestWorkers.runUntil(processor, THREADS, () -> recorder.count() >= expectedEvents, 15000);
        return recorder;
    }

    @RepeatedTest(5)
    void orderIsKeptPerEntityWithManyWorkers() throws InterruptedException {
        Transfer watched = newTransfer();
        for (int i = 0; i < TRANSFERS; i++) {
            newTransfer();
        }

        RecordingPublisher recorder = runWorkers((TRANSFERS + 1) * 2);

        List<RecordingPublisher.Sent> watchedSends = recorder.getSent().stream()
                .filter(s -> s.entityId().equals(watched.getId()))
                .sorted(Comparator.comparingLong(RecordingPublisher.Sent::order))
                .toList();

        assertThat(watchedSends).hasSize(2);
        assertThat(watchedSends.get(0).eventType()).isEqualTo(INITIATED);
        assertThat(watchedSends.get(1).eventType()).isEqualTo(COMPLETED);
    }

    @Test
    void eachEventIsSentOnlyOnce() throws InterruptedException {
        for (int i = 0; i < TRANSFERS; i++) {
            newTransfer();
        }

        RecordingPublisher recorder = runWorkers(TRANSFERS * 2);

        Set<UUID> uniqueIds = recorder.getSent().stream()
                .map(RecordingPublisher.Sent::eventId)
                .collect(Collectors.toSet());
        assertThat(recorder.getSent()).hasSize(TRANSFERS * 2);
        assertThat(uniqueIds).hasSize(TRANSFERS * 2);
    }

    @Test
    void allEventsEndUpPublished() throws InterruptedException {
        for (int i = 0; i < TRANSFERS; i++) {
            newTransfer();
        }

        runWorkers(TRANSFERS * 2);

        Integer notPublished = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status <> 'PUBLISHED'", Integer.class);
        assertThat(notPublished).isZero();
    }
}
