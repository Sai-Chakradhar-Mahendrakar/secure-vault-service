package com.vertex.securevaultservice.service.impl;

import com.vertex.securevaultservice.error.SecureVaultErrorType;
import com.vertex.securevaultservice.exception.SecureVaultException;
import com.vertex.securevaultservice.repository.dao.VaultRecordDao;
import com.vertex.securevaultservice.request.CreateVaultRecordRequest;
import com.vertex.securevaultservice.response.PagedResponse;
import com.vertex.securevaultservice.response.VaultRecordResponse;
import com.vertex.securevaultservice.service.UserKeyService;
import com.vertex.securevaultservice.service.VaultRecordService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletionStage;

@Service
public class VaultRecordServiceImpl implements VaultRecordService {
    private final VaultRecordDao vaultRecordDao;
    private final UserKeyService userKeyService;

    public VaultRecordServiceImpl(VaultRecordDao vaultRecordDao, UserKeyService userKeyService) {
        this.vaultRecordDao = vaultRecordDao;
        this.userKeyService = userKeyService;
    }

    @Override
    public CompletionStage<VaultRecordResponse> createVaultRecord(CreateVaultRecordRequest createVaultRecordRequest) {
        return userKeyService.findById(createVaultRecordRequest.userId())
                .thenCompose(existingUserKey -> {
                    if (existingUserKey.isEmpty()) {
                        throw new SecureVaultException(
                                "No UserKey registered for userId: " + createVaultRecordRequest.userId(),
                                SecureVaultErrorType.BAD_REQUEST
                        );
                    }
                    return vaultRecordDao.persist(createVaultRecordRequest.toEntity());
                })
                .thenApply(VaultRecordResponse::from);
    }

    @Override
    public CompletionStage<VaultRecordResponse> getVaultRecord(String vaultRecordId) {
        return vaultRecordDao.findWithId(vaultRecordId)
                .thenApply(vaultRecord -> {
                    if (vaultRecord.isEmpty()) {
                        throw new SecureVaultException(
                                "Vault record not found for id: " + vaultRecordId,
                                SecureVaultErrorType.NOT_FOUND
                        );
                    }
                    return VaultRecordResponse.from(vaultRecord.get());
                });
    }

    @Override
    public CompletionStage<PagedResponse<VaultRecordResponse>> getAllVaultRecords(String userId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return vaultRecordDao.getByUserId(userId, pageable)
                .thenApply(vaultRecordPage -> PagedResponse.from(vaultRecordPage.map(VaultRecordResponse::from)));
    }

    @Override
    public CompletionStage<VaultRecordResponse> deleteVaultRecord(String vaultRecordId) {
        return vaultRecordDao.findWithId(vaultRecordId)
                .thenCompose(vaultRecord -> {
                    if (vaultRecord.isEmpty()) {
                        throw new SecureVaultException(
                                "Vault record not found for id: " + vaultRecordId,
                                SecureVaultErrorType.NOT_FOUND
                        );
                    }
                    return vaultRecordDao.remove(vaultRecord.get());
                })
                .thenApply(VaultRecordResponse::from);
    }
}
