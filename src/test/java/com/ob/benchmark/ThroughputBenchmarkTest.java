package com.ob.benchmark;

import com.ob.IntegrationTestBase;
import com.ob.model.Transfer;
import com.ob.service.ClaimService;
import com.ob.service.RetryPolicy;
import com.ob.support.RecordingPublisher;
import com.ob.support.TestWorkers;
import com.ob.worker.EventProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ThroughputBenchmarkTest extends IntegrationTestBase {

    private static final int TRANSFERS = 500;
    private static final int EVENTS = TRANSFERS * 2;

    @Autowired
    ClaimService claimService;

    @Autowired
    RetryPolicy retryPolicy;

    @Test
    void benchmarkWithOneWorker() throws InterruptedException {
        runBenchmark(1);
    }

    @Test
    void benchmarkWithTwentyWorkers() throws InterruptedException {
        runBenchmark(20);
    }

    private void runBenchmark(int threads) throws InterruptedException {
        Map<UUID, Instant> createdAt = new HashMap<>();
        for (int i = 0; i < TRANSFERS; i++) {
            Transfer transfer = transferService.createTransfer(new BigDecimal("100.00"));
            createdAt.put(transfer.getId(), Instant.now());
        }

        RecordingPublisher recorder = new RecordingPublisher();
        EventProcessor processor = new EventProcessor(claimService, recorder, retryPolicy);

        Instant start = Instant.now();
        TestWorkers.runUntil(processor, threads, () -> recorder.count() >= EVENTS, 60000);
        long tookMs = Duration.between(start, Instant.now()).toMillis();

        Integer notPublished = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status <> 'PUBLISHED'", Integer.class);
        assertThat(notPublished).isZero();
        assertThat(recorder.count()).isEqualTo(EVENTS);

        double perSecond = EVENTS / (tookMs / 1000.0);

        List<Long> latencies = new ArrayList<>();
        for (RecordingPublisher.Sent sent : recorder.getSent()) {
            latencies.add(Duration.between(createdAt.get(sent.entityId()), sent.sentAt()).toMillis());
        }
        Collections.sort(latencies);

        System.out.println();
        System.out.println("----- benchmark: " + threads + " worker(s) -----");
        System.out.println("events      : " + EVENTS);
        System.out.println("time        : " + tookMs + " ms");
        System.out.println("throughput  : " + String.format("%.1f", perSecond) + " events/sec");
        System.out.println("p50 latency : " + percentile(latencies, 50) + " ms");
        System.out.println("p99 latency : " + percentile(latencies, 99) + " ms");
        System.out.println("--------------------------------");
    }

    private long percentile(List<Long> sorted, int percent) {
        int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
