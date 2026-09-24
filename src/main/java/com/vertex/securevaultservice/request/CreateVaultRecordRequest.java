package com.vertex.securevaultservice.request;

import com.vertex.securevaultservice.entity.VaultRecord;
import jakarta.validation.constraints.NotBlank;

public record CreateVaultRecordRequest(
        @NotBlank
        String userId,
        @NotBlank
        String encryptedPayload,
        @NotBlank
        String aesIv,
        @NotBlank
        String wrappedAesKey,
        @NotBlank
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
