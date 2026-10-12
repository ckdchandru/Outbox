package com.ob.worker;

import com.ob.IntegrationTestBase;
import com.ob.model.Transfer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class DeadLetterTest extends IntegrationTestBase {

    private static final int MAX_ATTEMPTS = 5;

    @Autowired
    EventProcessor processor;

    @Test
    void eventBecomesDeadExactlyAtMaxAttempts() {
        when(publisher.publish(any())).thenReturn(false);
        Transfer transfer = newTransfer();

        for (int i = 1; i < MAX_ATTEMPTS; i++) {
            makeReady(transfer.getId());
            processor.processOne("w1");
            assertThat(statusOf(transfer.getId(), INITIATED))
                    .as("after failure %d", i)
                    .isEqualTo("PENDING");
        }

        makeReady(transfer.getId());
        processor.processOne("w1");

        assertThat(statusOf(transfer.getId(), INITIATED)).isEqualTo("DEAD");
        assertThat(attemptsOf(transfer.getId(), INITIATED)).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    void deadEventDoesNotBlockNextEvent() {
        when(publisher.publish(any())).thenReturn(false);
        Transfer transfer = newTransfer();

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            makeReady(transfer.getId());
            processor.processOne("w1");
        }
        assertThat(statusOf(transfer.getId(), INITIATED)).isEqualTo("DEAD");
        assertThat(statusOf(transfer.getId(), COMPLETED)).isEqualTo("PENDING");

        reset(publisher);
        when(publisher.publish(any())).thenReturn(true);
        processor.processOne("w1");

        assertThat(statusOf(transfer.getId(), COMPLETED)).isEqualTo("PUBLISHED");
    }
}
