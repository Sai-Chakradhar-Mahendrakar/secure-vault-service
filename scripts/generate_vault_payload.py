#!/usr/bin/env python3
"""
Generates real RSA + ECDSA + ECDH crypto material and prints ready-to-paste
Postman requests for the secure-vault-service:

  1. POST /api/v1/users/{userId}/keys           (registers all three public keys)
  2. POST /api/v1/vault                         (AES-GCM encrypted, ECDSA-signed, HMAC-signed)
  3. GET  /api/v1/vault?userId=...              (HMAC-signed)
  4. GET  /api/v1/vault/{vaultRecordId}          (HMAC-signed -- needs --vault-record-id)
  5. DELETE /api/v1/vault/{vaultRecordId}        (HMAC-signed -- needs --vault-record-id)

Fetches the server's static ECDH public key from
GET /api/v1/keys/server/ecdh-public-key (the one network call this script
makes -- everything else is offline crypto generation) and derives the same
shared secret the server will derive, to compute the X-Request-Hmac header.

Not an automated test client -- a manual demo tool. Run it, copy the JSON
and headers it prints into Postman, and hit the endpoints in order. The
vaultRecordId used for GET-by-id/DELETE isn't known until the POST response
comes back, so pass it via --vault-record-id on a second run once you have it.

Keys are cached per --user-id under scripts/.vault_demo_keys/<user-id>.json so
that a later run with --vault-record-id reuses the *same* RSA/ECDSA/ECDH
keypairs already registered on the server, instead of generating fresh (and
therefore unregistered / mismatched) ones. Delete that file to start over
with a clean identity.

Usage:
    python3 scripts/generate_vault_payload.py [--user-id USER_ID] [--payload TEXT]
                                               [--base-url URL] [--vault-record-id ID]

Requires: pip install cryptography requests
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import time
import uuid

import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

DEFAULT_BASE_URL = "http://localhost:8080/secure-vault"
ECDH_PUBLIC_KEY_PATH = "/api/v1/keys/server/ecdh-public-key"
KEY_CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".vault_demo_keys")


def b64(data: bytes) -> str:
    return base64.b64encode(data).decode("ascii")


def generate_rsa_keypair() -> tuple[rsa.RSAPrivateKey, rsa.RSAPublicKey]:
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return private_key, private_key.public_key()


def generate_ecdsa_keypair() -> tuple[ec.EllipticCurvePrivateKey, ec.EllipticCurvePublicKey]:
    private_key = ec.generate_private_key(ec.SECP256R1())
    return private_key, private_key.public_key()


def public_key_to_base64_spki(public_key) -> str:
    der = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return b64(der)


def encrypt_payload(plaintext: bytes) -> tuple[str, str, bytes]:
    """AES-256-GCM encrypt. Returns (base64 ciphertext+tag, base64 iv, raw aes key)."""
    aes_key = os.urandom(32)
    iv = os.urandom(12)
    ciphertext = AESGCM(aes_key).encrypt(iv, plaintext, None)
    return b64(ciphertext), b64(iv), aes_key


def wrap_aes_key(rsa_public_key: rsa.RSAPublicKey, aes_key: bytes) -> str:
    wrapped = rsa_public_key.encrypt(
        aes_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )
    return b64(wrapped)


def sign_payload(ecdsa_private_key: ec.EllipticCurvePrivateKey, base64_encrypted_payload: str) -> str:
    # Signed content is the base64 ciphertext string itself, as UTF-8 bytes --
    # same bytes that go over the wire in encryptedPayload.
    signature = ecdsa_private_key.sign(
        base64_encrypted_payload.encode("utf-8"),
        ec.ECDSA(hashes.SHA256()),
    )
    return b64(signature)


def generate_ecdh_keypair() -> tuple[ec.EllipticCurvePrivateKey, ec.EllipticCurvePublicKey]:
    private_key = ec.generate_private_key(ec.SECP256R1())
    return private_key, private_key.public_key()


def _private_key_to_pem(private_key) -> str:
    return private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    ).decode("ascii")


def _cache_path(user_id: str) -> str:
    return os.path.join(KEY_CACHE_DIR, f"{user_id}.json")


def load_or_generate_keypairs(user_id: str):
    """Returns (rsa_private, ecdsa_private, ecdh_private), reusing a cached
    identity for user_id if one exists so repeated runs stay consistent with
    whatever was already registered on the server."""
    cache_path = _cache_path(user_id)
    if os.path.exists(cache_path):
        with open(cache_path, "r", encoding="utf-8") as cache_file:
            cached = json.load(cache_file)
        rsa_private_key = serialization.load_pem_private_key(cached["rsaPrivateKeyPem"].encode("ascii"), password=None)
        ecdsa_private_key = serialization.load_pem_private_key(cached["ecdsaPrivateKeyPem"].encode("ascii"), password=None)
        ecdh_private_key = serialization.load_pem_private_key(cached["ecdhPrivateKeyPem"].encode("ascii"), password=None)
        return rsa_private_key, ecdsa_private_key, ecdh_private_key

    rsa_private_key, _ = generate_rsa_keypair()
    ecdsa_private_key, _ = generate_ecdsa_keypair()
    ecdh_private_key, _ = generate_ecdh_keypair()

    os.makedirs(KEY_CACHE_DIR, exist_ok=True)
    with open(cache_path, "w", encoding="utf-8") as cache_file:
        json.dump(
            {
                "rsaPrivateKeyPem": _private_key_to_pem(rsa_private_key),
                "ecdsaPrivateKeyPem": _private_key_to_pem(ecdsa_private_key),
                "ecdhPrivateKeyPem": _private_key_to_pem(ecdh_private_key),
            },
            cache_file,
        )
    return rsa_private_key, ecdsa_private_key, ecdh_private_key


def fetch_server_ecdh_public_key(base_url: str) -> ec.EllipticCurvePublicKey:
    response = requests.get(f"{base_url}{ECDH_PUBLIC_KEY_PATH}", timeout=10)
    response.raise_for_status()
    der = base64.b64decode(response.json()["ecdhPublicKey"])
    return serialization.load_der_public_key(der)


def derive_hmac_key(
    client_private_key: ec.EllipticCurvePrivateKey,
    server_public_key: ec.EllipticCurvePublicKey,
) -> bytes:
    shared_secret = client_private_key.exchange(ec.ECDH(), server_public_key)
    return hashlib.sha256(shared_secret).digest()


def compute_request_hmac(hmac_key: bytes, method: str, path: str, query_string: str, timestamp_millis: str, body: str) -> str:
    canonical_request = "\n".join([method.upper(), path, query_string, timestamp_millis, body])
    signature = hmac.new(hmac_key, canonical_request.encode("utf-8"), hashlib.sha256).digest()
    return b64(signature)


def hmac_headers(hmac_key: bytes, user_id: str, method: str, path: str, query_string: str, body: str) -> dict:
    timestamp_millis = str(int(time.time() * 1000))
    request_hmac = compute_request_hmac(hmac_key, method, path, query_string, timestamp_millis, body)
    return {
        "X-User-Id": user_id,
        "X-Timestamp": timestamp_millis,
        "X-Request-Hmac": request_hmac,
    }


def print_request(method: str, path: str, headers: dict, body: str | None = None) -> None:
    print(f"{method} {path}")
    print(json.dumps(headers, indent=2))
    if body is not None:
        print(body)
    print()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--user-id", default=str(uuid.uuid4()), help="userId to use in all requests")
    parser.add_argument("--payload", default="hello from the vault", help="plaintext to encrypt and store")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="server base URL (host + context path)")
    parser.add_argument("--vault-record-id", default=None, help="known vaultRecordId, to demo GET-by-id/DELETE")
    args = parser.parse_args()

    is_new_identity = not os.path.exists(_cache_path(args.user_id))
    rsa_private_key, ecdsa_private_key, ecdh_private_key = load_or_generate_keypairs(args.user_id)
    rsa_public_key = rsa_private_key.public_key()
    ecdsa_public_key = ecdsa_private_key.public_key()
    ecdh_public_key = ecdh_private_key.public_key()

    server_ecdh_public_key = fetch_server_ecdh_public_key(args.base_url)
    hmac_key = derive_hmac_key(ecdh_private_key, server_ecdh_public_key)

    encrypted_payload_b64, iv_b64, aes_key = encrypt_payload(args.payload.encode("utf-8"))
    wrapped_aes_key_b64 = wrap_aes_key(rsa_public_key, aes_key)
    signature_b64 = sign_payload(ecdsa_private_key, encrypted_payload_b64)

    register_keys_body = {
        "rsaPublicKey": public_key_to_base64_spki(rsa_public_key),
        "ecdsaPublicKey": public_key_to_base64_spki(ecdsa_public_key),
        "ecdhPublicKey": public_key_to_base64_spki(ecdh_public_key),
    }

    create_vault_record_body = {
        "userId": args.user_id,
        "encryptedPayload": encrypted_payload_b64,
        "aesIv": iv_b64,
        "wrappedAesKey": wrapped_aes_key_b64,
        "digitalSignature": signature_b64,
    }
    # The HMAC must cover the exact bytes that go over the wire -- this must be
    # the same string that gets printed below and pasted verbatim into curl/Postman.
    create_vault_record_body_json = json.dumps(create_vault_record_body, indent=2)

    print("userId:", args.user_id)
    print()

    if is_new_identity:
        print(f"POST /api/v1/users/{args.user_id}/keys")
        print(json.dumps(register_keys_body, indent=2))
        print()
    else:
        print(f"(reusing cached identity for {args.user_id} -- already registered via a prior run;")
        print(f" delete {_cache_path(args.user_id)} to generate and register a new one instead)")
        print()

    print_request(
        "POST",
        "/api/v1/vault",
        hmac_headers(hmac_key, args.user_id, "POST", "/api/v1/vault", "", create_vault_record_body_json),
        create_vault_record_body_json,
    )

    list_query = f"userId={args.user_id}&page=0&size=10"
    print_request(
        "GET",
        f"/api/v1/vault?{list_query}",
        hmac_headers(hmac_key, args.user_id, "GET", "/api/v1/vault", list_query, ""),
    )

    if args.vault_record_id:
        print_request(
            "GET",
            f"/api/v1/vault/{args.vault_record_id}",
            hmac_headers(hmac_key, args.user_id, "GET", f"/api/v1/vault/{args.vault_record_id}", "", ""),
        )
        print_request(
            "DELETE",
            f"/api/v1/vault/{args.vault_record_id}",
            hmac_headers(hmac_key, args.user_id, "DELETE", f"/api/v1/vault/{args.vault_record_id}", "", ""),
        )
    else:
        print("(pass --vault-record-id <id> once you have one from the POST response, to also print")
        print(" the GET-by-id and DELETE requests -- their HMAC covers the id, so it can't be")
        print(" precomputed before the record exists.)")


if __name__ == "__main__":
    main()
