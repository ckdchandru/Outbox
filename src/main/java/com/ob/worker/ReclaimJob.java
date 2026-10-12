package com.ob.worker;

import com.ob.service.ReclaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "outbox.reclaim.enabled", havingValue = "true", matchIfMissing = true)
public class ReclaimJob {

    private static final Logger log = LoggerFactory.getLogger(ReclaimJob.class);

    private final ReclaimService reclaimService;

    public ReclaimJob(ReclaimService reclaimService) {
        this.reclaimService = reclaimService;
    }

    @Scheduled(fixedDelayString = "${outbox.reclaim.interval-ms:30000}",
            initialDelayString = "${outbox.reclaim.interval-ms:30000}")
    public void run() {
        try {
            int count = reclaimService.reclaimStuckEvents();
            if (count > 0) {
                log.info("Reclaimed {} stuck events", count);
            }
        } catch (Exception e) {
            log.error("Reclaim job failed: {}", e.getMessage());
        }
    }
}
