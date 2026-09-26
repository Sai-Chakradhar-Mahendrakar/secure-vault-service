package com.vertex.securevaultservice.request;

import com.vertex.securevaultservice.entity.UserKey;
import lombok.Builder;

@Builder
public record AddUserPublicKeyRequest(
        String rsaPublicKey,
        String ecdsaPublicKey,
        String ecdhPublicKey
) {
    public UserKey toEntity(String userId) {
        return UserKey.builder()
                .userId(userId)
                .rsaPublicKey(rsaPublicKey)
                .ecdsaPublicKey(ecdsaPublicKey)
                .ecdhPublicKey(ecdhPublicKey)
                .build();
    }
}
