# Phase 3: Transit — ECDH Handshake + HMAC-Signed Requests

## Context

This is the third of four phases building a "Zero-Knowledge Secure Vault" learning project (Spring Boot backend). The full roadmap:

- **Phase 1 (done):** Data at Rest — AES-encrypted vault storage with RSA key wrapping.
- **Phase 2 (done):** Identity — ECDSA digital signatures on uploads, verified server-side.
- **Phase 3 (this spec):** Transit — ECDH handshake + HMAC-signed requests.
- **Phase 4:** Hardware Auth — WebAuthn/FIDO2 attestation replacing basic auth.

Phase 2 shipped ECDSA verification on `POST /api/v1/vault` only, guarding the upload payload against forgery by someone who doesn't hold the private key. It left the whole channel otherwise open: `GET`/`DELETE` are unauthenticated, and even the protected `POST` only signs the `encryptedPayload` field — the rest of the request (method, path, query, headers, timing) carries no integrity or freshness guarantee. This spec closes that gap.

## Goal

Prove the request itself — not just the payload — wasn't tampered with or replayed in transit. Client and server derive a shared secret via ECDH (each side computes it independently from their own private key and the other's public key, without transmitting the secret). That shared secret keys an HMAC that covers a canonical representation of the whole request (method, path, query, timestamp, body). Every request to `/api/v1/vault/**` must carry a valid, fresh HMAC or it's rejected before reaching a controller.

This is a transit-integrity control, distinct from and additive to Phase 2's identity control:
- **ECDSA (Phase 2):** proves the caller holds a specific private key — an identity/possession check, scoped to the upload payload.
- **HMAC (Phase 3):** proves the request in flight wasn't altered or replayed — a transport check, scoped to the whole request, applied to reads and deletes too.

Both apply together on `POST /api/v1/vault`; `GET`/`GET-list`/`DELETE` gain only the new HMAC check, since Phase 2 never touched them.

## Non-goals (deferred to later phases)

- No encryption of request/response bodies — this is integrity/freshness only, still plain REST over HTTP (confidentiality in transit is out of scope; TLS would normally cover that in a real deployment).
- No nonce-based replay protection — a timestamp freshness window (5 minutes) is the only defense against replay. A captured request stays replayable until it falls outside that window. Full replay-proofing (nonce cache) is deferred; Phase 4's real authentication layer is a more appropriate place to revisit this.
- No key rotation, versioning, or session expiry — the server's ECDH keypair is generated once at startup and held for the process lifetime; a client's registered `ecdhPublicKey` follows the same upsert-overwrites semantics as `rsaPublicKey`/`ecdsaPublicKey`.
- No real authentication — `userId` is still a plain client-supplied field (`X-User-Id` header for this phase). A valid HMAC only proves the request matches a shared secret derived from *a* registered key, not that the caller is who they claim (Phase 4/WebAuthn territory).
- No change to Phase 1/2 crypto (AES-GCM payload encryption, RSA-OAEP key wrap, ECDSA signature) — all of that is unchanged and still required.

## Architecture

A new `HmacRequestInterceptor` (Spring `HandlerInterceptor`, registered via a `WebMvcConfigurer` for path pattern `/api/v1/vault/**`) runs in `preHandle`, before any vault controller method executes. It reads three request headers, rebuilds the canonical request string, derives the HMAC key from the ECDH shared secret, and verifies. A failure throws `SecureVaultException`, which the existing `@ControllerAdvice` (`SecureVaultExceptionHandler`) already catches — interceptor exceptions propagate through Spring MVC's normal exception resolution, so no separate error-response path is needed. This keeps error formatting consistent with every other failure in the service.

The server holds **one static P-256 ECDH keypair**, generated once at application startup and held in memory only (`ServerEcdhKeyHolder`, `@Component`). It is never persisted and never rotates during a run — acceptable for a learning-project demo; a production system would rotate and persist this.

New `crypto` package components, alongside `EcdsaSignatureVerifier`:

```java
// EcdhSharedSecretResolver
byte[] deriveSharedSecret(PrivateKey localPrivateKey, PublicKey remotePublicKey); // KeyAgreement.getInstance("ECDH")
byte[] deriveHmacKey(byte[] sharedSecret);                                       // SHA-256(sharedSecret)

// HmacRequestVerifier
boolean verify(byte[] hmacKey, String canonicalRequest, String base64Hmac);      // HmacSHA256, constant-time compare
```

Each is single-purpose and independently unit-testable with real generated keypairs, mirroring how `EcdsaSignatureVerifier` isolates its crypto behind one method. The raw ECDH shared secret is never used directly as a MAC key — it's hashed through SHA-256 first, a minimal one-step KDF (no need for a full HKDF implementation at this scope).

## Crypto parameters

- **Curve / key agreement:** P-256 (secp256r1) via `KeyAgreement.getInstance("ECDH")` — natively supported, same curve as Phase 2's ECDSA keys, no extra library.
- **Public key encoding:** base64-encoded X.509 `SubjectPublicKeyInfo`, same shape/encoding as the Phase 2 `ecdsaPublicKey`.
- **HMAC algorithm:** `HmacSHA256`, keyed by `SHA-256(sharedSecret)`.
- **HMAC encoding:** base64.
- **Canonical request string:**
  ```
  METHOD\nPATH\nQUERY_STRING\nTIMESTAMP\nBODY
  ```
  - `METHOD` — uppercase HTTP method (`POST`, `GET`, `DELETE`).
  - `PATH` — the request URI path, including path variables as they appear on the wire (e.g. `/api/v1/vault/abc-123`).
  - `QUERY_STRING` — raw query string exactly as sent (e.g. `userId=alice&page=0&size=10`), empty string if none.
  - `TIMESTAMP` — the exact value of the `X-Timestamp` header (epoch millis, as a string).
  - `BODY` — the raw request body for `POST`, empty string for `GET`/`DELETE` (no body on those).
  - Client and server must build this identically; the demo script constructs it the same way the interceptor does.

## Data model

### `UserKey` (existing entity, one new column)
- `userId`, `rsaPublicKey`, `ecdsaPublicKey` — unchanged from Phases 1–2.
- **`ecdhPublicKey`** (`@Column(length = 500)`, nullable) — new. Registered via the same upsert endpoint as the other two keys; independent of them (a client can have any subset registered).

No other schema changes. The shared secret itself is never persisted anywhere — it's recomputed per request from the server's in-memory static private key plus the caller's stored `ecdhPublicKey`.

## API changes

### `POST /api/v1/users/{userId}/keys` (existing endpoint, body extended again)
```json
{ "rsaPublicKey": "...", "ecdsaPublicKey": "...", "ecdhPublicKey": "..." }
```
Same partial-upsert semantics already in place for `rsaPublicKey`/`ecdsaPublicKey`: a field omitted (`null`) on an existing row leaves the previously-stored value untouched; a fresh registration with a field omitted stores it as `null`.

### `GET /api/v1/server/ecdh-public-key` (new endpoint)
Returns the server's static ECDH public key so a client can derive the same shared secret:
```json
{ "ecdhPublicKey": "base64-X.509-SPKI..." }
```
No request body, no auth required (it's a public key, and a client needs it before it can compute any HMAC — this endpoint is intentionally outside the `/api/v1/vault/**` interceptor scope).

### `/api/v1/vault/**` (all four existing endpoints — headers now required)
Every request to `POST /api/v1/vault`, `GET /api/v1/vault/{id}`, `GET /api/v1/vault?userId=`, and `DELETE /api/v1/vault/{id}` must carry:
```
X-User-Id: <userId>
X-Timestamp: <epoch millis>
X-Request-Hmac: <base64 HMAC over the canonical request string>
```
Verification sequence in `HmacRequestInterceptor.preHandle`:

1. Any of the three headers missing/blank → `401 UNAUTHORIZED` — `MISSING_OR_INVALID_HMAC`.
2. `|now - X-Timestamp| > 5 minutes` → `401 UNAUTHORIZED` — `STALE_REQUEST` (checked before HMAC verification — fail fast on an obviously-replayed request before doing any crypto work).
3. No `UserKey` row for `X-User-Id`, or `UserKey` exists but `ecdhPublicKey` is `null` → `401 UNAUTHORIZED` — `MISSING_OR_INVALID_HMAC` (deliberately the same error as an invalid HMAC — an unregistered key and a bad signature both mean "this request doesn't prove possession of a valid shared secret," and distinguishing them would leak whether a userId has registered transit keys).
4. HMAC recomputed from the canonical string and `SHA-256(ECDH shared secret)` doesn't match `X-Request-Hmac` → `401 UNAUTHORIZED` — `MISSING_OR_INVALID_HMAC`.
5. Passed → request proceeds to the controller, where Phase 1/2 logic (including the `POST /vault` ECDSA check) runs unchanged.

`POST /api/v1/vault`'s existing JSON body (`userId`, `encryptedPayload`, `aesIv`, `wrappedAesKey`, `digitalSignature`) is unchanged — the new headers sit alongside it, not inside it. Note `X-User-Id` and the body's `userId` field are independent values that happen to need to match for a real request to make sense, but the interceptor only uses the header; it does not cross-validate against the body (the service layer's existing `UserKey` lookup uses the body's `userId` as before).

## Validation & error handling

Two new `SecureVaultErrorType` entries:
```java
MISSING_OR_INVALID_HMAC("ERR_SV_010", "Request HMAC verification failed", HttpStatus.UNAUTHORIZED),
STALE_REQUEST("ERR_SV_011", "Request timestamp outside freshness window", HttpStatus.UNAUTHORIZED),
```
Thrown as `SecureVaultException` from `HmacRequestInterceptor`, exactly like existing errors. No changes needed to `SecureVaultExceptionHandler` — already generic over `SecureVaultErrorType`, and already proven to catch exceptions thrown ahead of controller execution (interceptors participate in the same Spring MVC exception-resolution path as controllers).

Malformed base64 in `ecdhPublicKey` or `X-Request-Hmac` (unparseable key/signature bytes) is treated as a verification failure (`HmacRequestVerifier.verify` catches the crypto exception and returns `false`) and surfaces as the same `401 MISSING_OR_INVALID_HMAC` — same pattern as Phase 2's `EcdsaSignatureVerifier`, including catching `IllegalArgumentException` from `Base64.getDecoder().decode(...)` on malformed input (the exact class of bug found and fixed in Phase 2's verifier).

## Persistence

No migration needed. `spring.jpa.hibernate.ddl-auto=update` adds the `ecdh_public_key` column to `user_keys` on next startup, consistent with how `ecdsa_public_key` was added in Phase 2.

## Testing

Extends the existing test approach (`@DataJpaTest`, `@WebMvcTest`/`MockMvc`):

- **`EcdhSharedSecretResolverTest`** (pure unit test, real generated P-256 keypairs, no Spring context): server-side derivation (`serverPrivate` + `clientPublic`) and client-side derivation (`clientPrivate` + `serverPublic`) produce the *same* shared secret — the core ECDH property. A different client keypair produces a different secret.
- **`HmacRequestVerifierTest`** (pure unit test): valid HMAC over the exact canonical string passes; tampering with method, path, query, timestamp, or body independently fails; a signature computed with a different HMAC key fails; malformed base64 in the HMAC input fails gracefully (returns `false`, no exception escapes).
- **`HmacRequestInterceptorTest`** (`@WebMvcTest` slice or full `MockMvc` integration): each of the 4 vault endpoints rejects with `401`/`MISSING_OR_INVALID_HMAC` on missing headers, on an unregistered `ecdhPublicKey`, and on a wrong HMAC; rejects with `401`/`STALE_REQUEST` on an old timestamp; accepts with a correctly computed HMAC and passes through to existing Phase 1/2 behavior (e.g. still returns `422` for a bad ECDSA signature on `POST /vault`, proving both checks are independently enforced).
- **`UserKeyService` upsert test**: registering `ecdhPublicKey` alongside or independently of the other two keys; confirms partial upserts don't null out the others (extending the existing test added for Phase 2's fix).

## Demo tooling

Extend `scripts/generate_vault_payload.py` (built in Phase 2):

1. Generate an ECDH (P-256) keypair alongside the existing RSA and ECDSA keypairs.
2. Fetch the server's public key from `GET /api/v1/server/ecdh-public-key` (the script becomes network-aware for this one call — everything else stays offline crypto generation).
3. Derive the shared secret (`KeyAgreement`-equivalent via `cryptography`'s ECDH support) and hash it to the HMAC key.
4. For each of the 4 vault requests it wants to demo, build the canonical string, compute `X-Request-Hmac`, and print the three headers alongside the existing JSON body.
5. Print ready-to-paste Postman requests: the extended `POST /users/{userId}/keys` body (all three public keys), and headers + body for `POST`, `GET`, `GET` (list), and `DELETE` against `/api/v1/vault`.

Still a manual demo tool for generating real crypto material to paste into Postman — not an automated test client.

## Implementation order

1. Add `ecdhPublicKey` to `UserKey` entity; extend `AddUserPublicKeyRequest` and `UserKeyServiceImpl.upsertUserKey` (reusing the existing null-preserving partial-upsert logic from the Phase 2 fix).
2. Add `MISSING_OR_INVALID_HMAC` and `STALE_REQUEST` to `SecureVaultErrorType`.
3. Build `ServerEcdhKeyHolder` (`@Component`, generates and holds the server's static P-256 keypair at startup).
4. Build `EcdhSharedSecretResolver` and `HmacRequestVerifier` in the `crypto` package.
5. Add `GET /api/v1/server/ecdh-public-key` endpoint.
6. Build `HmacRequestInterceptor` and register it via `WebMvcConfigurer` for `/api/v1/vault/**`.
7. Tests per the Testing section above.
8. Extend `scripts/generate_vault_payload.py` for manual Postman demos.
