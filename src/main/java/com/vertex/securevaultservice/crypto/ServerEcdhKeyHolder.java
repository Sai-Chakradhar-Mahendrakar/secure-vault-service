package com.vertex.securevaultservice.crypto;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

@Component
@Getter
public class ServerEcdhKeyHolder {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public ServerEcdhKeyHolder() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));

            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();

        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Could not initialize ECDH key pair generator", e);
        }
    }

    public String getBase64PublicKey() {
        return Base64.getEncoder().encodeToString(this.publicKey.getEncoded());
    }
}
