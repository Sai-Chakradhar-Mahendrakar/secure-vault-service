package com.vertex.securevaultservice.request;

import lombok.Builder;

@Builder
public record AddUserPublicKeyRequest(
        String rsaPublicKey
) {
}
