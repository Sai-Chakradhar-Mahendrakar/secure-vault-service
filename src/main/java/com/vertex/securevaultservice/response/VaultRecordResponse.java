package com.vertex.securevaultservice.response;

import com.vertex.securevaultservice.entity.VaultRecord;

import java.time.LocalDateTime;

public record VaultRecordResponse(
        String vaultRecordId,
        String userId,
        String encryptedPayload,
        String aesIv,
        String wrappedAesKey,
        String digitalSignature,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
    public static VaultRecordResponse from(VaultRecord vaultRecord) {
        return new VaultRecordResponse(
                vaultRecord.getVaultRecordId(),
                vaultRecord.getUserId(),
                vaultRecord.getEncryptedPayload(),
                vaultRecord.getAesIv(),
                vaultRecord.getWrappedAesKey(),
                vaultRecord.getDigitalSignature(),
                vaultRecord.getCreatedAt(),
                vaultRecord.getUpdatedAt(),
                vaultRecord.getVersion()
        );
    }
}
