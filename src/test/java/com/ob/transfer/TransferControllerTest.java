package com.ob.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ob.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferControllerTest extends IntegrationTestBase {

    @Autowired
    ObjectMapper objectMapper;

    private String createTransfer(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void postCreatesTransferAndTwoPendingEvents() throws Exception {
        String response = mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 300}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferId", notNullValue()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readTree(response).get("transferId").asText());
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE entity_id = ? AND status = 'PENDING'",
                Integer.class, id);
        assertThat(pending).isEqualTo(2);
    }

    @Test
    void zeroAmountGives400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAmountGives400() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("amount must not be null"));
    }

    @Test
    void getReturnsSavedTransfer() throws Exception {
        String response = createTransfer("{\"amount\": 150.50}");
        String id = objectMapper.readTree(response).get("transferId").asText();

        mockMvc.perform(get("/api/v1/transfers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(id))
                .andExpect(jsonPath("$.amount").value(150.50));
    }

    @Test
    void unknownTransferGives404() throws Exception {
        mockMvc.perform(get("/api/v1/transfers/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
