package com.vertex.securevaultservice.exception.handler;

import com.vertex.securevaultservice.error.SecureVaultError;
import com.vertex.securevaultservice.error.SecureVaultErrorType;
import com.vertex.securevaultservice.exception.SecureVaultException;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.Optional;

import static com.vertex.securevaultservice.error.SecureVaultErrorType.BAD_REQUEST;
import static com.vertex.securevaultservice.error.SecureVaultErrorType.INTERNAL_SERVER_ERROR;

@ControllerAdvice
public class SecureVaultExceptionHandler {
    @ExceptionHandler(value = { Exception.class })
    public ResponseEntity<SecureVaultError> handleGenericException(Exception ex) {
        return handleException(ex, INTERNAL_SERVER_ERROR, ex.getMessage(), INTERNAL_SERVER_ERROR.getHttpStatus());
    }

    @ExceptionHandler(value = {MissingServletRequestParameterException.class, HttpMessageNotReadableException.class, IllegalStateException.class,
            MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class, ValidationException.class, HttpMessageNotReadableException.class,
            HttpClientErrorException.BadRequest.class, IllegalArgumentException.class})
    public ResponseEntity<SecureVaultError> handleRequestValidationErrors(Exception ex) {
        return handleException(ex, BAD_REQUEST, ex.getMessage(), BAD_REQUEST.getHttpStatus());
    }

    @ExceptionHandler(value = SecureVaultException.class)
    public ResponseEntity<SecureVaultError> handleAtlasServiceException(SecureVaultException ex) {
        return handleException(
                ex,
                ex.getErrorType(),
                ex.getMessage(),
                ex.getErrorType().getHttpStatus(),
                Optional.ofNullable(ex.getAdditionalAttributes()));
    }

    private ResponseEntity<SecureVaultError> handleException(
            Exception ex,
            SecureVaultErrorType errorType,
            String errorMessage,
            HttpStatus httpStatus) {
        return handleException(ex, errorType, errorMessage, httpStatus, Optional.empty());
    }

    private ResponseEntity<SecureVaultError> handleException(
            Exception ex,
            SecureVaultErrorType errorType,
            String errorMessage,
            HttpStatus httpStatus,
            Optional<Map<String, Object>> additionalDetails
    ) {
        String message = errorMessage != null ? errorMessage : errorType.getErrorMessage();

        SecureVaultError error = SecureVaultError.builder()
                .traceId(null)
                .errorType(errorType)
                .errorMessage(message)
                .errorCode(errorType.getErrorCode())
                .additionalInfo(additionalDetails.orElse(null))
                .build();

        return new ResponseEntity<>(error, httpStatus);
    }
}
