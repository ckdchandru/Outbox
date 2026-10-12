package com.ob.service;

import com.ob.exception.NotFoundException;
import com.ob.model.Transfer;
import com.ob.model.TransferStatus;
import com.ob.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {

    public static final String TRANSFER_INITIATED = "TRANSFER_INITIATED";
    public static final String TRANSFER_COMPLETED = "TRANSFER_COMPLETED";

    private final TransferRepository transferRepository;
    private final OutboxService outboxService;

    public TransferService(TransferRepository transferRepository, OutboxService outboxService) {
        this.transferRepository = transferRepository;
        this.outboxService = outboxService;
    }

    // all or nothing
    @Transactional
    public Transfer createTransfer(BigDecimal amount) {
        Transfer transfer = new Transfer();
        transfer.setId(UUID.randomUUID());
        transfer.setAmount(amount);
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setCreatedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        outboxService.saveEvent(transfer.getId(), TRANSFER_INITIATED, buildPayload(transfer));

        transfer.setStatus(TransferStatus.COMPLETED);

        outboxService.saveEvent(transfer.getId(), TRANSFER_COMPLETED, buildPayload(transfer));

        return transfer;
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(UUID id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("transfer not found: " + id));
    }

    private Map<String, Object> buildPayload(Transfer transfer) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transferId", transfer.getId().toString());
        payload.put("amount", transfer.getAmount());
        payload.put("status", transfer.getStatus().name());
        return payload;
    }
}
