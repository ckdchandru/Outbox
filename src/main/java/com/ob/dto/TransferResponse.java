package com.ob.dto;

import com.ob.model.Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID transferId, BigDecimal amount, String status, Instant createdAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getAmount(),
                transfer.getStatus().name(),
                transfer.getCreatedAt());
    }
}
