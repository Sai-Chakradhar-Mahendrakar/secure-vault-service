package com.vertex.securevaultservice.response;

import lombok.Builder;

@Builder
public record UserKeyResponse(
        String userId,
        String
) {
}
