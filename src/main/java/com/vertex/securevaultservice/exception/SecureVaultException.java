package com.vertex.securevaultservice.exception;

import com.vertex.securevaultservice.error.SecureVaultErrorType;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class SecureVaultException extends RuntimeException{
    private final Map<String, Object> additionalAttributes = new HashMap<>();
    private final SecureVaultErrorType errorType;

    public SecureVaultException(String message, Throwable cause, SecureVaultErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
    }

    public SecureVaultException(String message, SecureVaultErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }
}
