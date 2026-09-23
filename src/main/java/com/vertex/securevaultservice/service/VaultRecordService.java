package com.vertex.securevaultservice.service;

import com.vertex.securevaultservice.request.CreateVaultRecordRequest;
import com.vertex.securevaultservice.response.VaultRecordResponse;
import org.springframework.data.domain.Page;

import java.util.concurrent.CompletionStage;

public interface VaultRecordService {
    CompletionStage<VaultRecordResponse> createVaultRecord(CreateVaultRecordRequest createVaultRecordRequest);

    CompletionStage<VaultRecordResponse> getVaultRecord(String vaultRecordId);

    CompletionStage<Page<VaultRecordResponse>> getAllVaultRecords(String userId, Integer page, Integer pageSize);

    CompletionStage<VaultRecordResponse> deleteVaultRecord(String vaultRecordId);
}
