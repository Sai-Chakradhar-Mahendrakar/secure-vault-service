package com.vertex.securevaultservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vault_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vault_record_id", length = 36, nullable = false, updatable = false)
    private String vaultRecordId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "encrypted_payload", length = 5000)
    private String encryptedPayload;

    @Column(name = "aes_iv")
    private String aesIv;

    @Column(name = "wrapped_aes_key", length = 1000)
    private String wrappedAesKey;

    @Column(name = "digital_signature", length = 1000)
    private String digitalSignature;
}
