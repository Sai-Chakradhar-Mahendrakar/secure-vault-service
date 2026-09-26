# Phase 4: Hardware Auth — WebAuthn/FIDO2

## Context

This is the fourth and final phase of the "Zero-Knowledge Secure Vault" learning project (Spring Boot backend). The full roadmap:

- **Phase 1 (done):** Data at Rest — AES-encrypted vault storage with RSA key wrapping.
- **Phase 2 (done):** Identity — ECDSA digital signatures on uploads, verified server-side.
- **Phase 3 (done):** Transit — ECDH handshake + HMAC-signed requests.
- **Phase 4 (this spec):** Hardware Auth — WebAuthn/FIDO2 attestation replacing basic auth.

Every prior phase proves "the caller holds a specific registered key" — but `userId` itself has always been a plain, trusted, client-supplied string (an `X-User-Id` header since Phase 3). Anyone who knows or guesses a `userId` can act as that user; nothing has ever verified that the caller *is* that person. This spec closes that gap with WebAuthn: a hardware authenticator (platform biometric or security key) proves possession of a private key that never leaves the device, and a successful login binds subsequent requests to a real server-side session instead of a bare header value.

## Goal

Replace the trusted `X-User-Id` header with a real authentication step. A user registers a WebAuthn authenticator once (bound to their existing `userId`); logging in requires a fresh WebAuthn assertion from that same authenticator. A successful login creates a server-side session; every `/api/v1/vault/**` request must now carry that session, and `userId` is read from the session rather than any client-supplied value. Phases 1–3's crypto (AES-GCM, RSA-OAEP, ECDSA, ECDH/HMAC) are unchanged and still fully enforced underneath — this phase only fixes how `userId` gets established in the first place.

## Non-goals (deferred / out of scope)

- No credential management UI/endpoints (list/revoke a registered authenticator) — one credential per `userId`; re-registering overwrites it, the same upsert semantics Phases 1–3 already use for `rsaPublicKey`/`ecdsaPublicKey`/`ecdhPublicKey`.
- No account recovery — a lost authenticator means lost access to that `userId`; out of scope, consistent with the "no key rotation/versioning" caveats carried since Phase 1.
- No multi-device/passkey-sync story — a single physical or platform authenticator per user, not several.
- No CSRF protection on the new cookie-based session endpoints — a production deployment would need it (typically free with Spring Security's session-management support); flagged as a known gap, deferred the same way TLS was deferred in Phase 3.
- No change to Phase 1/2/3 crypto or their verification logic — all of it remains required and unmodified.

## Architecture

A new `auth` package, parallel to `crypto`/`controller`/`service`:

```
auth/
  WebAuthnController.java        -- 4 endpoints: two ceremonies x (options, verify), plus logout
  WebAuthnCredentialService.java -- orchestrates webauthn4j, persists via UserKeyDao
  WebAuthnConfig.java             -- @Configuration: builds the WebAuthnManager + relying-party
                                     identity (rpId, rpName, origin) from application.properties
  SessionAuthInterceptor.java     -- new: rejects /api/v1/vault/** with no authenticated session
```

`org.webauthn4j:webauthn4j-core` handles all CBOR/COSE parsing, attestation/assertion verification, and challenge validation — no hand-rolled WebAuthn crypto, mirroring how Phase 2/3 delegated to `java.security.*` rather than reimplementing ECDSA/ECDH themselves.

### Ceremony flow

```
Registration ceremony (enroll a new authenticator for a userId):
  Browser -> GET  /api/v1/auth/register/options?userId=X   (server generates + session-stores a challenge)
  Browser -> navigator.credentials.create(...)              (authenticator signs the challenge)
  Browser -> POST /api/v1/auth/register/verify              (attestation object + challenge)
  Server: webauthn4j verifies attestation, extracts credentialId + COSE public key + signCount,
          upserts them onto the UserKey row for userId (same upsert pattern as the other 3 keys)

Authentication ceremony (login):
  Browser -> GET  /api/v1/auth/login/options?userId=X       (server loads stored credentialId,
                                                               generates + session-stores a challenge)
  Browser -> navigator.credentials.get(...)                  (authenticator signs the challenge)
  Browser -> POST /api/v1/auth/login/verify                  (assertion object + challenge)
  Server: webauthn4j verifies assertion against stored public key, checks signCount increased
          (replay/clone detection), stores userId in HttpSession -> login complete
```

The pending challenge (between `.../options` and `.../verify`) is stashed in the `HttpSession` itself — no separate challenge table, since the session already exists as infrastructure for this phase.

### Interceptor chain changes

Registration order on `/api/v1/vault/**`:

1. **`SessionAuthInterceptor`** (new) — no `HttpSession` with an authenticated `userId` attribute → `401 UNAUTHENTICATED`. Runs first, before any HMAC/crypto work.
2. **`HmacRequestInterceptor`** (changed) — reads `userId` from the session instead of the `X-User-Id` header; the freshness check, `ecdhPublicKey` lookup, and HMAC verification logic are all otherwise unchanged.

`CachedBodyFilter` (Phase 3) is untouched — still just buffers POST bodies so both the interceptor and controller can read them.

## Crypto parameters

- **Ceremony library:** `webauthn4j-core` — handles COSE key parsing, CBOR decoding of attestation/assertion objects, and challenge/origin/rpId validation internally.
- **Session mechanism:** Spring's built-in `HttpSession`, delivered via an `HttpOnly`+`Secure` cookie (`JSESSIONID`). No custom token signing/parsing — server-side session store holds `{userId}` after a successful login, invalidated on logout or expiry.
- **Public key encoding (stored):** base64-encoded COSE key bytes, as extracted by `webauthn4j` from the attestation object — different encoding from the X.509 SPKI base64 used by `rsaPublicKey`/`ecdsaPublicKey`/`ecdhPublicKey`, since WebAuthn's on-wire key format is COSE, not SPKI.

## Data model

### `UserKey` (existing entity, three new columns)
- `userId`, `rsaPublicKey`, `ecdsaPublicKey`, `ecdhPublicKey` — unchanged from Phases 1–3.
- **`webauthnCredentialId`** (`@Column(length = 500)`, nullable) — base64url-encoded credential id returned by the authenticator.
- **`webauthnPublicKeyCose`** (`@Column(length = 1000)`, nullable) — base64-encoded COSE public key extracted from the attestation object.
- **`webauthnSignCount`** (`Long`, nullable, default `0`) — the authenticator's signature counter; must strictly increase on every successful login. This is WebAuthn's core clone/replay defense: if a login's `signCount` doesn't exceed the stored value, the assertion is rejected.

No new table — one credential per `userId`, upserted via the same partial-update semantics already used for the other three key columns. Registering a new authenticator for a `userId` that already has one overwrites all three WebAuthn columns.

## API changes

### New endpoints (`/api/v1/auth/**`)
```
GET  /api/v1/auth/register/options?userId=X   -> PublicKeyCredentialCreationOptions (JSON); stashes challenge in session
POST /api/v1/auth/register/verify             -> body: {userId, attestationResponse}; verifies + upserts WebAuthn columns
GET  /api/v1/auth/login/options?userId=X      -> PublicKeyCredentialRequestOptions (JSON); stashes challenge in session
POST /api/v1/auth/login/verify                -> body: {userId, assertionResponse}; verifies signature + signCount,
                                                   stores userId in HttpSession
POST /api/v1/auth/logout                      -> invalidates the HttpSession
```
None of these sit under `/api/v1/vault/**`, so none of them go through `SessionAuthInterceptor`/`HmacRequestInterceptor` — they're the bootstrap/login path itself.

### `/api/v1/vault/**` (all four existing endpoints — auth model changes)
- `X-User-Id` header is **removed** from the API. `userId` is now read from the authenticated session by `HmacRequestInterceptor`.
- `X-Timestamp` and `X-Request-Hmac` headers, and the canonical-string format (`METHOD\nPATH\nQUERY_STRING\nTIMESTAMP\nBODY`), are unchanged from Phase 3 — only where `userId` comes from changes.
- A request with no session at all is rejected by `SessionAuthInterceptor` before reaching `HmacRequestInterceptor`.

### `POST /api/v1/users/{userId}/keys` (existing endpoint, unchanged)
Still the bootstrap registration point for `rsaPublicKey`/`ecdsaPublicKey`/`ecdhPublicKey`, and still unauthenticated — consistent with how this endpoint has worked since Phase 1. WebAuthn credential registration is a separate ceremony (`/api/v1/auth/register/*`), not folded into this endpoint.

## Validation & error handling

Two new `SecureVaultErrorType` entries:
```java
UNAUTHENTICATED("ERR_SV_012", "No authenticated session", HttpStatus.UNAUTHORIZED),
WEBAUTHN_VERIFICATION_FAILED("ERR_SV_013", "WebAuthn ceremony verification failed", HttpStatus.UNAUTHORIZED),
```
`SessionAuthInterceptor` throws `SecureVaultException(UNAUTHENTICATED)` when no session/`userId` attribute is present. `WebAuthnCredentialService` catches `webauthn4j`'s verification exceptions (bad attestation, bad assertion, non-increasing `signCount`, challenge/origin mismatch) and rethrows as `SecureVaultException(WEBAUTHN_VERIFICATION_FAILED)` — the same "catch the library's specific exception, normalize to one of ours" pattern `EcdsaSignatureVerifier` and `HmacRequestVerifier` already use. No changes needed to `SecureVaultExceptionHandler` — already generic over `SecureVaultErrorType`.

## Persistence

No migration needed. `spring.jpa.hibernate.ddl-auto=update` adds the three new `user_keys` columns on next startup, consistent with how `ecdsa_public_key` and `ecdh_public_key` were added in Phases 2 and 3.

## Testing

- **`WebAuthnCredentialServiceTest`**: registration ceremony with a valid attestation persists the three credential columns; a tampered/invalid attestation is rejected; authentication ceremony with a valid assertion succeeds and the stored `signCount` increases; a replayed assertion (stale/non-increasing `signCount`) is rejected.
- **`WebAuthnControllerTest`** (`@WebMvcTest`/`MockMvc`): options endpoints return well-formed challenge payloads with a session-stashed challenge; verify endpoints round-trip against `webauthn4j`'s own test/virtual-authenticator utilities.
- **`SessionAuthInterceptorTest`**: no session → `401 UNAUTHENTICATED`; valid session → passes through to the next interceptor.
- **`HmacRequestInterceptorTest`** (updated): `userId` now sourced from a mock session instead of the `X-User-Id` header; existing freshness/HMAC-mismatch cases unchanged.

## Demo tooling

WebAuthn ceremonies are **browser-API-bound** (`navigator.credentials.create`/`get`) and require an actual or virtual authenticator — unlike Phases 1–3's plain asymmetric keys, they cannot be generated offline by a standalone Python script. The demo tooling for this phase is therefore split:

1. **`scripts/webauthn_demo.html`** (new) — a minimal static page with inline JS that drives both ceremonies via `fetch()` against the real `/api/v1/auth/**` endpoints, meant to be opened in a browser and tested against a real platform authenticator (Touch ID, Windows Hello) or a virtual one (Chrome DevTools' WebAuthn tab). After a successful login it holds the session cookie the browser already manages.
2. **`scripts/generate_vault_payload.py`** (updated) — everything downstream of login is unchanged (AES/RSA/ECDSA/ECDH material, canonical-string HMAC signing), it just needs to stop constructing an `X-User-Id` header and instead expects the caller to supply a session cookie (e.g. via a `--session-cookie` flag) obtained from the HTML demo page's login flow.

## Implementation order

1. Add `org.webauthn4j:webauthn4j-core` to `pom.xml`.
2. Add `webauthnCredentialId`, `webauthnPublicKeyCose`, `webauthnSignCount` to the `UserKey` entity.
3. Add `UNAUTHENTICATED` and `WEBAUTHN_VERIFICATION_FAILED` to `SecureVaultErrorType`.
4. Build `WebAuthnConfig` (`WebAuthnManager` bean + relying-party identity from `application.properties`).
5. Build `WebAuthnCredentialService` and `WebAuthnController` (both ceremonies + logout).
6. Build `SessionAuthInterceptor`; update `HmacRequestInterceptor` to read `userId` from the session; register the new interceptor in `WebMvcConfig` ahead of the existing one, scoped to `/api/v1/vault/**`.
7. Tests per the Testing section above.
8. `scripts/webauthn_demo.html` (new); update `scripts/generate_vault_payload.py` to use a session cookie instead of `X-User-Id`.
