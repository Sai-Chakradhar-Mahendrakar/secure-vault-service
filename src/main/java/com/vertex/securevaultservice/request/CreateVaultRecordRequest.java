package com.vertex.securevaultservice.request;

import com.vertex.securevaultservice.entity.VaultRecord;

import java.util.UUID;

public record CreateVaultRecordRequest(
        String userId,
        String encryptedPayload,
        String aesIv,
        String wrappedAesKey,
        String digitalSignature
) {
    public VaultRecord toEntity() {
        return new VaultRecord(
                UUID.randomUUID().toString(),
                userId,
                encryptedPayload,
                aesIv,
                wrappedAesKey,
                digitalSignature
        );
    }
}
