package com.vertex.securevaultservice.response;

import com.vertex.securevaultservice.entity.VaultRecord;

public record VaultRecordResponse(
        String vaultRecordId,
        String userId,
        String encryptedPayload,
        String aesIv,
        String wrappedAesKey,
        String digitalSignature
) {
    public static VaultRecordResponse from(VaultRecord vaultRecord) {
        return new VaultRecordResponse(
                vaultRecord.getVaultRecordId(),
                vaultRecord.getUserId(),
                vaultRecord.getEncryptedPayload(),
                vaultRecord.getAesIv(),
                vaultRecord.getWrappedAesKey(),
                vaultRecord.getDigitalSignature()
        );
    }
}
