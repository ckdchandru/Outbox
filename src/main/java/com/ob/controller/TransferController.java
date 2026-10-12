package com.ob.controller;

import com.ob.dto.TransferRequest;
import com.ob.dto.TransferResponse;
import com.ob.model.Transfer;
import com.ob.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest request) {
        Transfer transfer = transferService.createTransfer(request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(transferService.getTransfer(id));
    }
}
