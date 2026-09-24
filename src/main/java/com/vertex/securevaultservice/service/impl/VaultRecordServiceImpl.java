package com.vertex.securevaultservice.service.impl;

import com.vertex.securevaultservice.crypto.EcdsaSignatureVerifier;
import com.vertex.securevaultservice.entity.UserKey;
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
    private final EcdsaSignatureVerifier ecdsaSignatureVerifier;

    public VaultRecordServiceImpl(VaultRecordDao vaultRecordDao, UserKeyService userKeyService, EcdsaSignatureVerifier ecdsaSignatureVerifier) {
        this.vaultRecordDao = vaultRecordDao;
        this.userKeyService = userKeyService;
        this.ecdsaSignatureVerifier = ecdsaSignatureVerifier;
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
                    UserKey userKey = existingUserKey.get();
                    if (userKey.getEcdsaPublicKey() == null) {
                        throw new SecureVaultException(
                                "UserKey for userId: " + createVaultRecordRequest.userId() + " does not have an ECDSA public key registered.",
                                SecureVaultErrorType.BAD_REQUEST
                        );
                    }
                    boolean valid = ecdsaSignatureVerifier.verifySignature(
                            userKey.getEcdsaPublicKey(),
                            createVaultRecordRequest.digitalSignature(),
                            createVaultRecordRequest.encryptedPayload()
                    );
                    if (!valid) {
                        throw new SecureVaultException(
                                "Invalid digital signature for userId: " + createVaultRecordRequest.userId(),
                                SecureVaultErrorType.INVALID_SIGNATURE
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
