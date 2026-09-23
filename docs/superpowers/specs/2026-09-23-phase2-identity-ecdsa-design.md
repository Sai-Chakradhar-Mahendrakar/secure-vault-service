# Phase 2: Identity — ECDSA Digital Signatures

## Context

This is the second of four phases building a "Zero-Knowledge Secure Vault" learning project (Spring Boot backend). The full roadmap:

- **Phase 1 (done):** Data at Rest — AES-encrypted vault storage with RSA key wrapping.
- **Phase 2 (this spec):** Identity — ECDSA digital signatures on uploads, verified server-side.
- **Phase 3:** Transit — ECDH handshake + HMAC-signed requests.
- **Phase 4:** Hardware Auth — WebAuthn/FIDO2 attestation replacing basic auth.

Phase 1 shipped `POST /api/v1/users/{userId}/keys`, `POST /api/v1/vault`, `GET /api/v1/vault/{id}`, `GET /api/v1/vault?userId=`, and `DELETE /api/v1/vault/{id}`, all tested in Postman. The `VaultRecord.digitalSignature` column already exists but is unused. This spec makes it real.

## Goal

Prove the client actually controls the private key behind an upload: every `POST /api/v1/vault` must carry an ECDSA signature over the encrypted payload, verified server-side against a public key the client registered in advance. An attacker who doesn't hold the private key — even one who can read/guess a valid `userId` — cannot get a forged record accepted.

## Non-goals (deferred to later phases)

- No signature requirement on `GET`/`DELETE` — this phase only guards the write path (`POST /api/v1/vault`).
- No key rotation or versioning — registering a key still fully overwrites the previous one (same upsert semantics as Phase 1's `rsaPublicKey`).
- No ECDH/HMAC transit security — still plain REST over HTTP (Phase 3).
- No real authentication — `userId` is still a plain client-supplied field; a valid signature only proves possession of *a* private key, not that the caller is who they claim (Phase 4/WebAuthn territory).

## Architecture

A dedicated `EcdsaSignatureVerifier` component (new `crypto` package, `@Component`) isolates all `java.security.*` key-parsing and signature-verification logic behind one method:

```java
boolean verify(String base64PublicKey, String base64Signature, String signedContent);
```

It's injected into `VaultRecordServiceImpl`, keeping crypto plumbing out of business logic and independently unit-testable — mirroring how `CustomThreadPool` and `BaseRepository` already isolate cross-cutting concerns in this codebase. No new controller, no new top-level endpoint: verification is a step inside the existing `createVaultRecord` flow.

## Crypto parameters

- **Curve / algorithm:** P-256 (secp256r1) with SHA256withECDSA — natively supported by `java.security.Signature`, no extra library needed.
- **Public key encoding:** base64-encoded X.509 `SubjectPublicKeyInfo` (same shape as an RSA public key would be, just shorter — a P-256 key is ~250 base64 chars vs. RSA-2048's ~400).
- **Signature encoding:** base64-encoded DER signature — the default output format of Java's `Signature.sign()`.
- **Signed content:** `encryptedPayload` only (the base64 ciphertext string, signed as UTF-8 bytes). Not a concatenation of all fields — the payload is the thing worth protecting from tampering, and this keeps client-side signing simple (sign after encrypt, same bytes you already have).

## Data model

### `UserKey` (existing entity, one new column)
- `userId`, `rsaPublicKey` — unchanged from Phase 1.
- **`ecdsaPublicKey`** (`@Column(length = 500)`, nullable) — new. Registered via the same upsert endpoint as `rsaPublicKey`; a user can have one without the other (e.g. a Phase-1-only client that hasn't upgraded).

### `VaultRecord` (existing entity, no schema change)
- `digitalSignature` (`@Column(length = 1000)`) already exists from Phase 1. It transitions from "present but unvalidated" to **required and verified**.

## API changes

### `POST /api/v1/users/{userId}/keys` (existing endpoint, body extended)
Request body gains an optional second field:
```json
{ "rsaPublicKey": "...", "ecdsaPublicKey": "..." }
```
Upsert semantics unchanged: whichever fields are present overwrite the stored row; omitting `ecdsaPublicKey` leaves the previously-stored value untouched only if the row already exists — a fresh registration with `ecdsaPublicKey` omitted stores it as `null`.

### `POST /api/v1/vault` (existing endpoint, `digitalSignature` now required and verified)
`CreateVaultRecordRequest.digitalSignature` becomes `@NotBlank` (was optional/unvalidated in Phase 1). Verification sequence, extending the existing "UserKey must exist" check:

1. No `UserKey` row for `userId` → `400 BAD_REQUEST` — `"No UserKey registered for userId: X"` (unchanged from Phase 1).
2. `UserKey` exists but `ecdsaPublicKey` is `null` → `400 BAD_REQUEST` — `"No ECDSA public key registered for userId: X"` (new).
3. `ecdsaPublicKey` present but `EcdsaSignatureVerifier.verify(...)` returns `false` → `422 UNPROCESSABLE_ENTITY` — `"Signature verification failed for userId: X"` (new).
4. Verified → persist, same as Phase 1.

`GET /api/v1/vault/{id}`, `GET /api/v1/vault?userId=`, and `DELETE /api/v1/vault/{id}` are unchanged.

## Validation & error handling

New `SecureVaultErrorType` entry:

```java
INVALID_SIGNATURE("ERR_SV_009", "Signature verification failed", HttpStatus.UNPROCESSABLE_ENTITY),
```

Thrown as a `SecureVaultException` from `VaultRecordServiceImpl`, exactly like the existing `NOT_FOUND`/`BAD_REQUEST` cases. No changes needed to `SecureVaultExceptionHandler` — it's already generic over `SecureVaultErrorType`.

Malformed base64 in `ecdsaPublicKey` or `digitalSignature` (unparseable key/signature bytes) is treated as a verification failure (`EcdsaSignatureVerifier.verify` catches the crypto exception and returns `false`), not a separate error path — the caller gets the same `422 INVALID_SIGNATURE` either way.

## Persistence

No migration needed. `spring.jpa.hibernate.ddl-auto=update` will add the `ecdsa_public_key` column to `user_keys` on next startup, consistent with how the existing columns were created.

## Testing

Extends the existing Phase 1 test approach (`@DataJpaTest`, `@WebMvcTest`/`MockMvc`):

- **`EcdsaSignatureVerifierTest`** (pure unit test, real generated P-256 keypairs, no Spring context): valid signature over the exact signed content passes; a tampered payload fails; a signature from the wrong keypair fails; malformed base64 in either input fails gracefully (no exception escapes — returns `false`).
- **`VaultRecordServiceImpl` / controller tests**: `201` with a valid signature; `400` when no `UserKey` exists; `400` when `UserKey` exists but has no `ecdsaPublicKey`; `422` when the signature doesn't verify.
- **`UserKeyService` upsert test**: registering `ecdsaPublicKey` alongside or independently of `rsaPublicKey`; confirms partial upserts don't null out the other key.

## Demo tooling

Build `scripts/generate_vault_payload.py` (standalone, outside the Maven build — speced in Phase 1 but never built, so it covers both phases' crypto in one place):

1. Generate an RSA keypair and a P-256 ECDSA keypair.
2. Generate an AES-256 key; encrypt a sample payload with AES-GCM.
3. Wrap the AES key with RSA-OAEP.
4. Sign the base64 `encryptedPayload` with the ECDSA private key (SHA256withECDSA).
5. Print two ready-to-paste JSON bodies: the `POST /users/{userId}/keys` body (both public keys) and the `POST /api/v1/vault` body (encrypted payload, IV, wrapped key, signature).

This is a manual demo tool for generating real crypto material to paste into Postman — not an automated test client.

## Implementation order

1. Add `ecdsaPublicKey` to `UserKey` entity.
2. Extend `AddUserPublicKeyRequest` with optional `ecdsaPublicKey`; update `UserKeyServiceImpl.upsertUserKey` to set it when present.
3. Add `INVALID_SIGNATURE` to `SecureVaultErrorType`.
4. Build `EcdsaSignatureVerifier` in a new `crypto` package (`verify(base64PublicKey, base64Signature, signedContent)`, catches crypto exceptions internally, returns boolean).
5. Make `CreateVaultRecordRequest.digitalSignature` `@NotBlank`.
6. Extend `VaultRecordServiceImpl.createVaultRecord` with the ecdsaPublicKey-presence check and the `EcdsaSignatureVerifier` call, in sequence after the existing `UserKey`-exists check.
7. Tests per the Testing section above.
8. `scripts/generate_vault_payload.py` for manual Postman demos.
