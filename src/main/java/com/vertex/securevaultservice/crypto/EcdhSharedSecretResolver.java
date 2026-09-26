package com.vertex.securevaultservice.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.KeyAgreement;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class EcdhSharedSecretResolver {
    public byte[] deriveSharedSecret(PrivateKey localPrivateKey, PublicKey remotePublicKey)
            throws NoSuchAlgorithmException, InvalidKeyException {
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(localPrivateKey);
        keyAgreement.doPhase(remotePublicKey, true);
        return keyAgreement.generateSecret();
    }

    private byte[] deriveHmacKey(byte[] sharedSecret) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(sharedSecret);
    }

    public PublicKey decodePublicKey(String base64PublicKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("EC").generatePublic(keySpec);
    }
}
