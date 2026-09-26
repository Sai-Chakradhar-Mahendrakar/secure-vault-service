package com.vertex.securevaultservice.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class HmacRequestVerifier {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public boolean verifySignature(byte[] hmacKey, String canonicalRequest, String base64Hmac) {
        try {
            byte[] expected = computeHmac(hmacKey, canonicalRequest);
            byte[] provided = Base64.getDecoder().decode(base64Hmac);
            return MessageDigest.isEqual(expected, provided);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] computeHmac(byte[] hmacKey, String canonicalRequest)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
        return mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8));
    }
}
