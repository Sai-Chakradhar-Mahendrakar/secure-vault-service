package com.vertex.securevaultservice.request;

import com.vertex.securevaultservice.entity.VaultRecord;

public record CreateVaultRecordRequest(
        String userId,
        String encryptedPayload,
        String aesIv,
        String wrappedAesKey,
        String digitalSignature
) {
    public VaultRecord toEntity() {
        return VaultRecord.builder()
                .userId(userId)
                .encryptedPayload(encryptedPayload)
                .aesIv(aesIv)
                .wrappedAesKey(wrappedAesKey)
                .digitalSignature(digitalSignature)
                .build();
    }
}
