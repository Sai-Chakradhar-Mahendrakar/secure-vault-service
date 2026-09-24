package com.vertex.securevaultservice.error;

import lombok.Builder;

import java.util.Map;

@Builder
public record SecureVaultError (
        String traceId,
        String errorCode,
        String errorMessage,
        SecureVaultErrorType errorType,
        Map<String, Object> additionalInfo
) {
}
