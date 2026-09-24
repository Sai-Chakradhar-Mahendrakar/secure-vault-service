package com.vertex.securevaultservice.crypto;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class EcdsaSignatureVerifier {
    public boolean verifySignature(String base64PublicKey, String base64Signature, String signedContent) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(base64PublicKey);
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);

            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            Signature ecdsaSignature = Signature.getInstance("SHA256withECDSA");
            ecdsaSignature.initVerify(publicKey);
            ecdsaSignature.update(signedContent.getBytes(StandardCharsets.UTF_8));
            return ecdsaSignature.verify(signatureBytes);

        } catch (IllegalArgumentException | InvalidKeySpecException | InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            return false;
        }
    }
}
