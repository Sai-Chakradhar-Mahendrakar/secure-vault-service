# Copilot instructions for secure-vault-service

Zero-knowledge vault storage service (Spring Boot 4.1.1, Java 25, Postgres, Hibernate). The client does all cryptography (AES-256-GCM encrypt, RSA-OAEP key wrap, ECDSA sign) before anything reaches the server — the server stores/verifies ciphertext and signatures, it never sees plaintext or private key material. Full plan: `docs/superpowers/specs/` (one design doc per phase; check the most recent one for current phase scope).

## Architecture

Layered: `controller → service (interface + impl) → repository/dao`, with DTOs (`request`/`response` packages) at the API boundary — entities never cross into controllers.

- **Entity → DTO conversion convention:** request DTOs get an instance `toEntity()` method (they own the data, converting themselves); response DTOs get a static `from(entity)` factory (keeps the entity ignorant of the response package — dependency points one way, response → entity, never the reverse).
- **Async pattern:** all service/repository methods return `CompletionStage<T>`, not blocking calls. `BaseRepository` provides `persist`, `findWithId`, `remove` as `CompletionStage`-wrapped defaults over `JpaRepository`, using `CustomThreadPool` (separate DB/computation/HTTP executors — see `configuration/CustomThreadPool.java`). Don't add a new async method that reuses the *same method name* as the sync one it wraps (return-type-only overloading isn't valid Java and this bit us once already) — follow the `UserKeyDao.getByUserId` pattern (distinct name wrapping `findByUserId`).
- **Error handling:** all business errors are `SecureVaultException(message, SecureVaultErrorType)`. `SecureVaultErrorType` is the single source of truth for error code + message + HTTP status. `SecureVaultExceptionHandler` is `@ControllerAdvice` (NOT `@Configuration` — that silently disables the handler, already happened once) and is generic over any `SecureVaultErrorType`, so adding a new error type never requires touching the handler.
- **Pagination:** controllers/services return `PagedResponse<T>` (own type: `content`, `page`, `size`, `totalElements`, `totalPages`), never a raw Spring Data `Page<T>` — raw `Page` serializes with a lot of internal `pageable`/`sort` clutter that isn't meant for API consumers.

## Known gotchas (don't reintroduce)

- **Lombok + this JDK:** `pom.xml`'s lombok dependency must be `<scope>provided</scope>` (not `annotationProcessor`, which is not a valid Maven scope) **and** the `maven-compiler-plugin` must declare `annotationProcessorPaths` explicitly pointing at lombok. On this JDK/javac build, annotation processors are not auto-discovered from plain `-classpath` — omitting `annotationProcessorPaths` causes every Lombok-generated method to silently not exist, producing a wall of unrelated "cannot find symbol" compile errors.
- **Entity IDs:** don't manually assign a value to an `@Id` field that's also `@GeneratedValue` (e.g. `UUID.randomUUID().toString()` in a `toEntity()` method). Combined with a `@Version` field starting `null`, Hibernate treats the entity as detached rather than new and throws on save. Use the Lombok `@SuperBuilder` and leave the generated field unset.
- **`BaseEntity.updatedAt`** is `@UpdateTimestamp(insertable = false)` — intentionally null until first update. The Postgres columns must allow `NULL` on `updated_at`; don't add a `NOT NULL` constraint there.

## Review priorities for this repo

1. **Crypto correctness over style.** This is a security-sensitive service — flag anything that could let unverified/unsigned/unencrypted data through, weakens a check (e.g. optional-where-should-be-required), or logs/echoes secret material (private keys, raw payloads) in errors or responses.
2. **Async correctness.** Watch for blocking calls inside `CompletionStage` chains, missing `.thenCompose`/`.thenApply` wiring, and exceptions thrown inside a lambda that won't map cleanly to `SecureVaultExceptionHandler` (it does handle exceptions surfaced via `CompletionException`/`ExecutionException` at the controller boundary, but a swallowed/logged-only exception inside a chain is a real bug).
3. **DTO boundary.** No entity should be returned directly from a controller; no request DTO should be persisted directly without going through `toEntity()`.
4. **Consistency with the current phase spec** in `docs/superpowers/specs/` — flag anything that contradicts or skips what that doc says (e.g. an unverified signature field, a missing required-field check).
