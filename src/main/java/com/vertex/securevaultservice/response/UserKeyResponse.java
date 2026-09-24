package com.vertex.securevaultservice.response;

import com.vertex.securevaultservice.entity.UserKey;

import java.time.LocalDateTime;

public record UserKeyResponse(
        String userId,
        String rsaPublicKey,
        String ecdsaPublicKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
    public static UserKeyResponse from(UserKey userKey) {
        return new UserKeyResponse(
                userKey.getUserId(),
                userKey.getRsaPublicKey(),
                userKey.getEcdsaPublicKey(),
                userKey.getCreatedAt(),
                userKey.getUpdatedAt(),
                userKey.getVersion()
        );
    }
}
