#!/usr/bin/env python3
"""
Generates real RSA + ECDSA crypto material and prints two ready-to-paste
Postman bodies for the secure-vault-service:

  1. POST /api/v1/users/{userId}/keys   (registers both public keys)
  2. POST /api/v1/vault                 (an AES-GCM encrypted, ECDSA-signed payload)

Not an automated test client -- a manual demo tool. Run it, copy the JSON
it prints into Postman, and hit the two endpoints in order.

Usage:
    python3 scripts/generate_vault_payload.py [--user-id USER_ID] [--payload TEXT]

Requires: pip install cryptography
"""

import argparse
import base64
import json
import os
import uuid

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


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


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--user-id", default=str(uuid.uuid4()), help="userId to use in both requests")
    parser.add_argument("--payload", default="hello from the vault", help="plaintext to encrypt and store")
    args = parser.parse_args()

    rsa_private_key, rsa_public_key = generate_rsa_keypair()
    ecdsa_private_key, ecdsa_public_key = generate_ecdsa_keypair()

    encrypted_payload_b64, iv_b64, aes_key = encrypt_payload(args.payload.encode("utf-8"))
    wrapped_aes_key_b64 = wrap_aes_key(rsa_public_key, aes_key)
    signature_b64 = sign_payload(ecdsa_private_key, encrypted_payload_b64)

    register_keys_body = {
        "rsaPublicKey": public_key_to_base64_spki(rsa_public_key),
        "ecdsaPublicKey": public_key_to_base64_spki(ecdsa_public_key),
    }

    create_vault_record_body = {
        "userId": args.user_id,
        "encryptedPayload": encrypted_payload_b64,
        "aesIv": iv_b64,
        "wrappedAesKey": wrapped_aes_key_b64,
        "digitalSignature": signature_b64,
    }

    print("userId:", args.user_id)
    print()
    print(f"POST /api/v1/users/{args.user_id}/keys")
    print(json.dumps(register_keys_body, indent=2))
    print()
    print("POST /api/v1/vault")
    print(json.dumps(create_vault_record_body, indent=2))


if __name__ == "__main__":
    main()
