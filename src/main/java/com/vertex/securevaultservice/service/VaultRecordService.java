package com.vertex.securevaultservice.service;

import com.vertex.securevaultservice.request.CreateVaultRecordRequest;
import com.vertex.securevaultservice.response.PagedResponse;
import com.vertex.securevaultservice.response.VaultRecordResponse;

import java.util.concurrent.CompletionStage;

public interface VaultRecordService {
    CompletionStage<VaultRecordResponse> createVaultRecord(CreateVaultRecordRequest createVaultRecordRequest);

    CompletionStage<VaultRecordResponse> getVaultRecord(String vaultRecordId);

    CompletionStage<PagedResponse<VaultRecordResponse>> getAllVaultRecords(String userId, Integer page, Integer size);

    CompletionStage<VaultRecordResponse> deleteVaultRecord(String vaultRecordId);
}
