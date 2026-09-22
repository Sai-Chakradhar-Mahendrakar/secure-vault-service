# Phase 1: Data at Rest — Zero-Knowledge Vault Storage

## Context

This is the first of four phases building a "Zero-Knowledge Secure Vault" learning project (Spring Boot backend, resume-grade but scoped as a learning exercise). The full roadmap:

- **Phase 1 (this spec):** Data at Rest — AES-encrypted vault storage with RSA key wrapping.
- **Phase 2:** Identity — ECDSA digital signatures on uploads, verified server-side.
- **Phase 3:** Transit — ECDH handshake + HMAC-signed requests.
- **Phase 4:** Hardware Auth — WebAuthn/FIDO2 attestation replacing basic auth.

Each phase gets its own spec once the prior phase is implemented and demoed. This spec covers Phase 1 only.

## Goal

Prove out a true zero-knowledge storage flow: the client does all cryptography (AES-256-GCM encrypt, RSA-OAEP key wrap) before anything reaches the server. The server stores and serves ciphertext, wrapped keys, and public keys — it never sees plaintext or private key material.

## Non-goals (deferred to later phases)

- No real authentication (`userId` is a plain client-supplied field; no Spring Security).
- No digital signature verification (the `digitalSignature` column exists but is unused/unvalidated).
- No ECDH/HMAC transit security — plain REST over HTTP for this phase.

## Architecture

Standard layered Spring Boot: `Controller → Service → Repository`, with DTOs at the API boundary (entities are never exposed directly). Packages: `entity`, `dto`, `service`, `controller`, `repository`, plus the already-built `error`/`exception` packages.

Crypto happens entirely client-side. A standalone Python script (`scripts/generate_vault_payload.py`, outside the Maven build) generates an RSA keypair, an AES-256 key, encrypts a sample payload with AES-GCM, wraps the AES key with RSA-OAEP, and prints base64 values to paste into Postman requests. This is a manual demo tool, not an automated test client.

## Data model

Two entities:

### `UserKey` (already scaffolded, needs one fix)
- `userId` (`@Id`, **plain string, no `@GeneratedValue`**) — client-supplied identifier (e.g. `"alice"`), must match the `userId` used on `VaultRecord`. The existing entity has `@GeneratedValue(strategy = GenerationType.UUID)` on `userId`, which would silently overwrite the client's id with a random UUID — this must be removed.
- `rsaPublicKey` (`@Column(length = 2000)`)

One row per user; registered once before any vault upload for that user. POSTing again with the same `userId` upserts (overwrites) the stored key.

### `VaultRecord` (already scaffolded, matches design)
- `vaultRecordId` (`@Id`, `@GeneratedValue(strategy = GenerationType.UUID)`)
- `userId` (`@Column(nullable = false)`)
- `encryptedPayload` (`@Column(length = 5000)`)
- `aesIv`
- `wrappedAesKey` (`@Column(length = 1000)`)
- `digitalSignature` (`@Column(length = 1000)`, nullable, unused until Phase 2)

## API endpoints

- `POST /api/v1/users/{userId}/keys` — upsert the user's RSA public key. Body: `{ rsaPublicKey }`.
- `POST /api/v1/vault` — create a record. Body: `{ userId, encryptedPayload, aesIv, wrappedAesKey }`. Returns 400 (`BAD_REQUEST`) if no `UserKey` is registered for that `userId`.
- `GET /api/v1/vault/{vaultRecordId}` — fetch one record. 404 (`NOT_FOUND`) if missing.
- `GET /api/v1/vault?userId=...` — list a user's records.
- `DELETE /api/v1/vault/{vaultRecordId}` — delete a record. 404 if missing.

## Validation & error handling

`spring-boot-starter-validation` (already in `pom.xml`) drives `@NotBlank` on DTO fields. The existing `SecureVaultExceptionHandler` / `SecureVaultException` / `SecureVaultErrorType` framework is reused as-is:
- Missing `UserKey` on vault create, or missing `VaultRecord` on get/delete → `SecureVaultException` with `NOT_FOUND` or a new `USER_KEY_NOT_FOUND` entry as appropriate.
- Bean validation failures on request bodies → handled by the existing `HttpMessageNotReadableException`/`MissingServletRequestParameterException` handlers; a `MethodArgumentNotValidException` handler should be added for `@Valid` body validation, mapped to `BAD_REQUEST`.

## Persistence

Postgres, already configured in `application.properties` (`jdbc:postgresql://localhost:5432/postgres`, `ddl-auto=update`). The `org.postgresql:postgresql` runtime dependency is missing from `pom.xml` and must be added.

## Testing

Standard Spring `@DataJpaTest` (repositories) and `@WebMvcTest`/`MockMvc` (controllers) tests, covering: successful create/get/list/delete, 404 on missing record, 400 on vault create with no registered `UserKey`, and validation failures on blank fields. These are ordinary engineering tests, separate from the Python script (which is for generating real crypto material to demo against Postman, not for automated testing).

## Implementation order

1. Fix `UserKey.userId` (remove `@GeneratedValue`).
2. Add `org.postgresql:postgresql` to `pom.xml`.
3. DTOs: `UserKeyRequest`, `VaultRecordRequest`, `VaultRecordResponse`.
4. Repositories: `UserKeyRepository`, `VaultRecordRepository`.
5. Services: `UserKeyService`, `VaultService` (with the "UserKey must exist" check).
6. Controllers: `UserKeyController`, `VaultController`.
7. Add `USER_KEY_NOT_FOUND` (or reuse `NOT_FOUND`) to `SecureVaultErrorType` if needed; add `MethodArgumentNotValidException` handler.
8. Tests per the Testing section above.
9. `scripts/generate_vault_payload.py` for manual Postman demos.
