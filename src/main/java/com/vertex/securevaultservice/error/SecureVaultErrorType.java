package com.vertex.securevaultservice.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SecureVaultErrorType {
    INTERNAL_SERVER_ERROR("ERR_SV_000", "Unexpected server error occurred.",HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED("ERR_SV_001", "Unauthorized request", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("ERR_SV_002", "Forbidden request", HttpStatus.FORBIDDEN),
    NOT_FOUND("ERR_SV_003", "Entry not found", HttpStatus.NOT_FOUND),
    ALREADY_EXISTS("ERR_SV_004", "Entry already exists", HttpStatus.CONFLICT),
    BAD_REQUEST("ERR_SV_005", "Bad request", HttpStatus.BAD_REQUEST),
    COLLECTION_WRITE_ERROR("ERR_SV_006", "Error in writing to collection", HttpStatus.INTERNAL_SERVER_ERROR),
    JSON_PARSING_ERROR("ERR_SV_007", "Failed to parse JSON payload", HttpStatus.BAD_REQUEST),
    EVENT_PUBLISHING_ERROR("ERR_SV_008", "Failed to publish event to Kafka", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String errorCode;
    private final String errorMessage;
    private final HttpStatus httpStatus;
}
