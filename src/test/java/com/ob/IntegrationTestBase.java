package com.ob;

import com.ob.model.Transfer;
import com.ob.publisher.EventPublisher;
import com.ob.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String INITIATED = TransferService.TRANSFER_INITIATED;
    protected static final String COMPLETED = TransferService.TRANSFER_COMPLETED;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TransferService transferService;

    // mock
    @MockBean
    protected EventPublisher publisher;

    @BeforeEach
    void clearTables() {
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM transfers");
    }

    protected Transfer newTransfer() {
        return transferService.createTransfer(new BigDecimal("250.00"));
    }

    protected String statusOf(UUID entityId, String eventType) {
        return jdbc.queryForObject(
                "SELECT status FROM outbox_events WHERE entity_id = ? AND event_type = ?",
                String.class, entityId, eventType);
    }

    protected UUID eventIdOf(UUID entityId, String eventType) {
        return jdbc.queryForObject(
                "SELECT id FROM outbox_events WHERE entity_id = ? AND event_type = ?",
                UUID.class, entityId, eventType);
    }

    protected int attemptsOf(UUID entityId, String eventType) {
        return jdbc.queryForObject(
                "SELECT attempts FROM outbox_events WHERE entity_id = ? AND event_type = ?",
                Integer.class, entityId, eventType);
    }

    // skip the backoff wait
    protected void makeReady(UUID entityId) {
        jdbc.update("UPDATE outbox_events SET available_at = now() WHERE entity_id = ? AND status = 'PENDING'",
                entityId);
    }
}
