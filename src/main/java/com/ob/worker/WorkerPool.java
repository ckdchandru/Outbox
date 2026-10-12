package com.ob.worker;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "outbox.worker.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerPool {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

    private final EventProcessor eventProcessor;
    private final int threads;
    private final long idleSleepMs;

    private ExecutorService executor;
    private volatile boolean running = false;

    public WorkerPool(EventProcessor eventProcessor,
                      @Value("${outbox.worker.threads:4}") int threads,
                      @Value("${outbox.worker.idle-sleep-ms:200}") long idleSleepMs) {
        this.eventProcessor = eventProcessor;
        this.threads = threads;
        this.idleSleepMs = idleSleepMs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        executor = Executors.newFixedThreadPool(threads);

        String instanceId = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; i <= threads; i++) {
            String workerName = "worker-" + instanceId + "-" + i;
            executor.submit(() -> runLoop(workerName));
        }
        log.info("Started {} outbox workers", threads);
    }

    private void runLoop(String workerName) {
        while (running) {
            try {
                boolean didWork = eventProcessor.processOne(workerName);
                if (!didWork) {
                    Thread.sleep(idleSleepMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // keep worker alive
                log.error("{} hit an error: {}", workerName, e.getMessage());
                sleepQuietly();
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(idleSleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        running = false;
        if (executor == null) {
            return;
        }
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        log.info("Outbox workers stopped");
    }
}
