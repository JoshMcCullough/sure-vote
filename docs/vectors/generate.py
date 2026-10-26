# Independent reference vectors for SureVote's algorithm identifiers, from pyca/cryptography (OpenSSL) and argon2-cffi.
import json
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature, encode_dss_signature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from argon2.low_level import hash_secret_raw, Type

N = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
def key(byte): return ec.derive_private_key(int(byte * 32, 16), ec.SECP256R1())
def point(k): return k.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)

recipient = key("11"); ephemeral = key("22")
R = point(recipient); E = point(ephemeral)
SEAL = "ecies-p256-hkdf-sha256-aes256gcm"
shared = ephemeral.exchange(ec.ECDH(), recipient.public_key())
info = SEAL.encode() + b"\x00" + E + R
derived = HKDF(hashes.SHA256(), 44, salt=None, info=info).derive(shared)
body = AESGCM(derived[:32]).encrypt(derived[32:], b"correct horse battery", None)
assert AESGCM(derived[:32]).decrypt(derived[32:], body, None) == b"correct horse battery"

SIG = "ecdsa-p256-sha256"
HASH = "ab4e9d991e5d0874756969031a20998cd489b5a92c322ffaa61c9ee866f48c0c"
statement = json.dumps({"alg": SIG, "hash": HASH}, separators=(",", ":")).encode()
der = recipient.sign(statement, ec.ECDSA(hashes.SHA256(), deterministic_signing=True))  # RFC 6979
r, s = decode_dss_signature(der)
low = min(s, N - s); high = N - low
recipient.public_key().verify(encode_dss_signature(r, low), statement, ec.ECDSA(hashes.SHA256()))
recipient.public_key().verify(encode_dss_signature(r, high), statement, ec.ECDSA(hashes.SHA256()))
hx = lambda v: v.to_bytes(32, "big").hex()

mail = hash_secret_raw(b"correct horse battery", bytes.fromhex("33" * 32), 3, 65536, 1, 32, Type.ID, 0x13)
print(json.dumps({
    "viewingPublicKey": SEAL + ":" + R.hex(),
    "signingPublicKey": SIG + ":" + R.hex(),
    "ephemeralPublic": E.hex(), "shared": shared.hex(), "hkdfInfo": info.hex(), "derived": derived.hex(),
    "sealed": SEAL + ":" + (E + body).hex(), "body": body.hex(),
    "hash": HASH, "statement": statement.decode(),
    "lowS": SIG + ":" + hx(r) + hx(low), "highS": SIG + ":" + hx(r) + hx(high),
    "inPersonCode": "44" * 32,
    "mailAccessHash": "argon2id-v13:m=65536,t=3,p=1:" + mail.hex(),
}, indent=2))
