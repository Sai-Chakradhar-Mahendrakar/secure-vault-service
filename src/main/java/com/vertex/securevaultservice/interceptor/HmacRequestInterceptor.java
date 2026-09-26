package com.vertex.securevaultservice.interceptor;

import com.vertex.securevaultservice.crypto.EcdhSharedSecretResolver;
import com.vertex.securevaultservice.crypto.HmacRequestVerifier;
import com.vertex.securevaultservice.crypto.ServerEcdhKeyHolder;
import com.vertex.securevaultservice.entity.UserKey;
import com.vertex.securevaultservice.exception.SecureVaultException;
import com.vertex.securevaultservice.service.UserKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.time.Clock;

import static io.micrometer.common.util.StringUtils.isBlank;
import static com.vertex.securevaultservice.error.SecureVaultErrorType.MISSING_OR_INVALID_HMAC;
import static com.vertex.securevaultservice.error.SecureVaultErrorType.STALE_REQUEST;


@Component
public class HmacRequestInterceptor implements HandlerInterceptor {
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_HMAC = "X-Request-Hmac";

    private static final long MAX_TIMESTAMP_AGE_MILLIS = 5 * 60 * 1000L;

    private final UserKeyService userKeyService;
    private final EcdhSharedSecretResolver ecdhSharedSecretResolver;
    private final ServerEcdhKeyHolder serverEcdhKeyHolder;
    private final HmacRequestVerifier hmacRequestVerifier;

    private final Clock clock = Clock.systemUTC();

    public HmacRequestInterceptor(
            UserKeyService userKeyService,
            EcdhSharedSecretResolver ecdhSharedSecretResolver,
            ServerEcdhKeyHolder serverEcdhKeyHolder,
            HmacRequestVerifier hmacRequestVerifier) {
        this.userKeyService = userKeyService;
        this.ecdhSharedSecretResolver = ecdhSharedSecretResolver;
        this.serverEcdhKeyHolder = serverEcdhKeyHolder;
        this.hmacRequestVerifier = hmacRequestVerifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(HEADER_USER_ID);
        String timestampHeader = request.getHeader(HEADER_TIMESTAMP);
        String providedHmac = request.getHeader(HEADER_HMAC);

        if (isBlank(userId) || isBlank(timestampHeader) || isBlank(providedHmac)) {
            throw new SecureVaultException(MISSING_OR_INVALID_HMAC.getErrorMessage(), MISSING_OR_INVALID_HMAC);
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            throw new SecureVaultException(MISSING_OR_INVALID_HMAC.getErrorMessage(), MISSING_OR_INVALID_HMAC);
        }

        long now = clock.millis();
        if (Math.abs(now - timestamp) > MAX_TIMESTAMP_AGE_MILLIS) {
            throw new SecureVaultException(STALE_REQUEST.getErrorMessage(), STALE_REQUEST);
        }

        UserKey userKey = userKeyService.findById(userId)
                .toCompletableFuture()
                .join()
                .orElseThrow(() -> new SecureVaultException(MISSING_OR_INVALID_HMAC.getErrorMessage(), MISSING_OR_INVALID_HMAC));

        if (isBlank(userKey.getEcdhPublicKey())) {
            throw new SecureVaultException(MISSING_OR_INVALID_HMAC.getErrorMessage(), MISSING_OR_INVALID_HMAC);
        }

        byte[] hmacKey;
        try {
            PublicKey remotePublicKey = ecdhSharedSecretResolver.decodePublicKey(userKey.getEcdhPublicKey());
            byte[] sharedSecret = ecdhSharedSecretResolver.deriveSharedSecret(serverEcdhKeyHolder.getPrivateKey(), remotePublicKey);
            hmacKey = ecdhSharedSecretResolver.deriveHmacKey(sharedSecret);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new SecureVaultException(MISSING_OR_INVALID_HMAC.getErrorMessage(), MISSING_OR_INVALID_HMAC);
        }

        String canonicalRequest = buildCanonicalRequest(request, timestampHeader);

        if (!hmacRequestVerifier.verifySignature(hmacKey, canonicalRequest, providedHmac)) {
            throw new SecureVaultException(MISSING_OR_INVALID_HMAC.getErrorMessage(), MISSING_OR_INVALID_HMAC);
        }

        return true;
    }

    private String buildCanonicalRequest(HttpServletRequest request, String timestampHeader) {
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String queryString = request.getQueryString() != null ? request.getQueryString() : "";
        String body = readBody(request);

        return String.join("\n", method, path, queryString, timestampHeader, body);
    }

    private String readBody(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return "";
        }
        try {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
