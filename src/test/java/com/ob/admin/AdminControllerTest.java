package com.ob.admin;

import com.ob.IntegrationTestBase;
import com.ob.model.Transfer;
import com.ob.worker.EventProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest extends IntegrationTestBase {

    private static final String TOKEN = "test-token";

    @Autowired
    EventProcessor processor;

    // fail until dead
    private UUID makeInitiatedDead(UUID entityId) {
        when(publisher.publish(any())).thenReturn(false);
        for (int i = 0; i < 5; i++) {
            makeReady(entityId);
            processor.processOne("w1");
        }
        assertThat(statusOf(entityId, INITIATED)).isEqualTo("DEAD");
        return eventIdOf(entityId, INITIATED);
    }

    @Test
    void deadEventIsListed() throws Exception {
        Transfer transfer = newTransfer();
        UUID deadId = makeInitiatedDead(transfer.getId());

        mockMvc.perform(get("/api/v1/admin/outbox/dead").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(deadId.toString()))
                .andExpect(jsonPath("$.content[0].attempts").value(5));
    }

    @Test
    void requestWithoutTokenGives401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/outbox/dead"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/outbox/dead").header("X-Admin-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requeuedEventIsPublishedAgain() throws Exception {
        Transfer transfer = newTransfer();
        UUID deadId = makeInitiatedDead(transfer.getId());

        mockMvc.perform(post("/api/v1/admin/outbox/" + deadId + "/requeue").header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(attemptsOf(transfer.getId(), INITIATED)).isZero();

        reset(publisher);
        when(publisher.publish(any())).thenReturn(true);
        processor.processOne("w1");

        assertThat(statusOf(transfer.getId(), INITIATED)).isEqualTo("PUBLISHED");
    }

    @Test
    void requeueOfPendingEventGives409() throws Exception {
        Transfer transfer = newTransfer();
        UUID pendingId = eventIdOf(transfer.getId(), INITIATED);

        mockMvc.perform(post("/api/v1/admin/outbox/" + pendingId + "/requeue").header("X-Admin-Token", TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    void requeueOfUnknownEventGives404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/outbox/" + UUID.randomUUID() + "/requeue")
                        .header("X-Admin-Token", TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void singleEventCanBeInspected() throws Exception {
        Transfer transfer = newTransfer();
        UUID eventId = eventIdOf(transfer.getId(), INITIATED);

        mockMvc.perform(get("/api/v1/admin/outbox/" + eventId).header("X-Admin-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value(INITIATED))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.maxAttempts").value(5));
    }
}
