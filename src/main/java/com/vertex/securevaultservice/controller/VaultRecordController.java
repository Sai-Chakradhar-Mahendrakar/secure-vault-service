package com.vertex.securevaultservice.controller;

import com.vertex.securevaultservice.request.CreateVaultRecordRequest;
import com.vertex.securevaultservice.response.VaultRecordResponse;
import com.vertex.securevaultservice.service.VaultRecordService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletionStage;

@RestController
@RequestMapping("/api/v1/vault")
public class VaultRecordController {
    private final VaultRecordService vaultRecordService;

    public VaultRecordController(VaultRecordService vaultRecordService) {
        this.vaultRecordService = vaultRecordService;
    }

    @PostMapping
    public CompletionStage<ResponseEntity<VaultRecordResponse>> createVaultRecord(@RequestBody CreateVaultRecordRequest createVaultRecordRequest) {
        return vaultRecordService.createVaultRecord(createVaultRecordRequest)
                .thenApply(vaultRecordResponse -> ResponseEntity.status(HttpStatus.CREATED).body(vaultRecordResponse));
    }

    @GetMapping("/{vaultRecordId}")
    public CompletionStage<ResponseEntity<VaultRecordResponse>> getVaultRecord(@PathVariable String vaultRecordId) {
        return vaultRecordService.getVaultRecord(vaultRecordId)
                .thenApply(vaultRecordResponse -> ResponseEntity.ok(vaultRecordResponse));
    }

    @GetMapping
    public CompletionStage<ResponseEntity<Page<VaultRecordResponse>>> getVaultRecordsForUser(
            @RequestParam String userId,
            @RequestParam Integer page,
            @RequestParam Integer pageSize) {
        return vaultRecordService.getAllVaultRecords(userId, page, pageSize)
                .thenApply(vaultRecordResponsePage -> ResponseEntity.ok(vaultRecordResponsePage));
    }
}
