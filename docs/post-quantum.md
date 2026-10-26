# Post-quantum

This page says which of Sure Vote's primitives a large quantum computer would break, what that would expose for each asset, and how the system would migrate. The algorithm identifiers at the end are decided and implemented; the byte layouts are in [verification.md](./verification.md#algorithm-identifiers).

## Which primitives hold

Two quantum algorithms matter. Grover's algorithm speeds up brute-force search quadratically, so a k-bit key gives about k/2 bits of security, and the search runs as about 2^(k/2) steps that cannot be spread out cheaply. Shor's algorithm solves factoring and elliptic-curve discrete logs in polynomial time, which breaks the scheme outright.

| Primitive | Where | Against a quantum attacker |
| --- | --- | --- |
| SHA-256 | payload and chain hashes, in-person `voterAccessHash`, `tokenId` | Holds. Grover gives about 128-bit preimage resistance. Quantum collision search gives no practical gain over the classical 2^128. |
| AES-256-GCM | the DEM of every sealed box and of escrow | Holds. About 128-bit security under Grover. |
| HKDF-SHA256 | key derivation in ECIES and escrow | Holds, as SHA-256 does. Its output is only as strong as the shared secret fed in, and that secret comes from P-256 (below). |
| Argon2id | mail-in `voterAccessHash` | Holds. It has no key to recover. Grover could at most square-root a search over passphrases. Here the 32-byte access code is the salt, so the search is per code, and the code is 256 random bits. The FIPS question about Argon2id is separate and still open. |
| ECDSA P-256 | every signature: device, import, operator, validator, request | Broken by Shor. Whoever can run it can forge any signature from the public key. |
| ECIES / ECDH on P-256 | sealed viewing passphrases, YubiKey PIV ECDH | Broken by Shor. A recorded sealed box can be opened from the public key alone. |
| Threshold ElGamal on P-256 | device-voting escrow | Broken by Shor. The election key `Y` is public, so every escrow ciphertext opens without any trustee. |
| RSA-3072 blind signatures (RFC 9474) | device-voting tokens | Signatures can be forged once N is factored. Blindness holds (below). |

## What breaking them would expose

**Signatures (forgery is future-only).** A forged signature does not rewrite history that was already witnessed. Past heads are held outside the host: validators signed them when they saw them ([system.md](./system.md#outside-copies)), head notices and close bundles sit in the object-locked published bucket, and anyone may keep a copy. A forger could sign a different chain, but it would conflict with copies made before the attacker had a quantum computer. What protects the past is those independent, dated copies, not the algorithm. Once forgery is practical, new signatures under P-256 prove nothing, so the migration has to land before then.

**Ballot secrecy (by design, not by encryption).** Ballots on the public chain carry selections and no identity. Secrecy comes from unlinkability: no stored value joins a voter to a ballot ([system.md](./system.md#who-can-see-what)). Breaking a public-key scheme does not add a link that was never stored.

**Device tokens (stay blind).** RSA blind signatures are statistically blinding: with a uniform blinding factor, every possible token is equally likely for any blinded message the signer saw, whatever the signer can compute. Factoring N later lets an attacker forge new tokens. That matters only for an election still open. It does not unblind tokens already issued. Here `r` is not uniform. It is derived from the voter's 128-bit seed with hash-based derivation, which a quantum computer does not break, so blindness rests on that derivation. Grover bounds a search for the seed at about 2^64 sequential steps, each a full derivation checked against the stored `blinded`. That is far from practical, and it is the smallest margin in this table. Decided: the phrase stays 12 words (128 bits). NIST's view is that 128-bit symmetric security holds against Grover in practice: the speedup is quadratic only in theory, quantum steps are costly, and Grover parallelizes poorly, so NIST expects AES-128 to stay secure for decades and does not ask for longer symmetric keys ([NIST PQC FAQ](https://csrc.nist.gov/projects/post-quantum-cryptography/faqs); [NIST SP 800-131A Rev. 3 (initial public draft)](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-131Ar3.ipd.pdf)). A seed search is a 128-bit symmetric search of the same kind.

**Harvest now, decrypt later.** An attacker who records ciphertext today could open it once Shor is practical. Three things are recordable:

- **Sealed viewing passphrases.** They are in the private roll and its backups. Opening one gives a mail-in voter's passphrase. That reveals no selections by itself. The lookup hash also needs that voter's 32-byte access code, which is never stored. But the passphrase together with the code from the voter's letter, email, or text does find that voter's public ballot. So the exposure is the private roll plus the delivered code for the same voter.
- **Escrow ciphertexts.** They are on device issuances. Opening one gives that voter's `(m, r)` and so `tokenId`, and the spent list shows whether the token was cast, surrendered, or revoked. It reveals no selections, because the spent list cannot be joined to a ballot ([device-voting.md](./device-voting.md#escrow)). It does reveal, for each device voter, whether they cast by device. Shares are destroyed after certification, but the public key `Y` is enough for Shor, so destroying shares does not close this.
- **TLS captures.** Hybrid post-quantum key exchange in TLS protects new sessions from this. A recorded lookup session is the sensitive one: the request carries the code and, for mail-in, the passphrase, and the response carries the ballot, so a capture ties the client's address to the selections. Workspace sessions carry private-roll data.

## Migration plan

The standards are final and the JDK has them. ML-KEM (FIPS 203) and ML-DSA (FIPS 204) shipped in JDK 24 (JEP 496 and JEP 497), so the JDK 25 build can use them through the standard `KEM`, `KeyPairGenerator`, and `Signature` APIs without Bouncy Castle.

1. **Hybrid first, never pure.** Each change keeps P-256 alongside the post-quantum algorithm, so security holds if either one holds.
   - Sealing: hybrid KEM, P-256 ECDH plus ML-KEM-768, both shared secrets through HKDF-SHA256 into AES-256-GCM. The viewing passphrase and the escrow DEM key move first, because they are the harvest-now targets.
   - Signatures: dual signatures, ECDSA P-256 and ML-DSA (ML-DSA-65 is the likely set), both required to verify. Forgery is future-only, so this can follow sealing.
   - Escrow: threshold ElGamal has no drop-in lattice threshold scheme. The first step is the hybrid KEM above, with the ML-KEM private key split k-of-n on the same ceremony. A threshold ML-KEM design needs its own review.
   - RSA blind tokens: no standardized post-quantum blind signature exists yet. Blindness already holds, so this waits for a standard.
2. **Gated on FIPS-validated hardware.** Production keys live on YubiKey 5 FIPS and the authority HSM. Neither does ML-KEM or ML-DSA in a FIPS-validated mode today. Production moves when validated hardware supports the new algorithms. Until then, software keys can use them only in development, tests, and the showcase.
3. **Hybrid post-quantum TLS where AWS has it.** What AWS documents today:
   - **CloudFront** (lookup, demo, ingest): viewer connections over TLS 1.3 support `X25519MLKEM768` and `SecP256r1MLKEM768`, on existing security policies, with no change ([CloudFront viewer protocols and ciphers](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/secure-connections-supported-viewer-protocols-ciphers.html); [AWS post-quantum confidentiality table](https://aws.amazon.com/security/post-quantum-cryptography/aws-pq-readiness-confidentiality/)). TLS 1.2 gets no post-quantum key exchange. That covers browser-to-edge only. These pages do not say CloudFront-to-origin connections are post-quantum.
   - **Application Load Balancer** (behind the workspace and the origins): opt-in. A listener must use a `PQ` policy such as `ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09`, or the `FIPS-PQ` variant. The default for listeners made through CloudFormation or the CDK is still `ELBSecurityPolicy-2016-08` ([ALB security policies](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/describe-ssl-policies.html)). The workspace ALB's HTTPS listener is defined in `SureVoteWorkspaceAccess` with `ELBSecurityPolicy-TLS13-1-2-Res-PQ-2025-09` (`albSslPolicy`, which accepts only the four `PQ` policies, FIPS variants included), and a CDK test checks that every ALB listener in the app uses one.
   - **Verified Access** (workspace): AWS documents TLS 1.2 or later and recommends 1.3, with no post-quantum key exchange ([Verified Access data protection](https://docs.aws.amazon.com/verified-access/latest/ug/data-protection.html)). It is not in AWS's post-quantum table. Treat workspace sessions as classical until AWS says otherwise.
   - **Clients.** Hybrid TLS is used only when the client offers it. Current Chrome, Edge, and Firefox do. The JDK 25 TLS client this build uses does not. JDK 27 adds `X25519MLKEM768` and prefers it by default ([JEP 527](https://openjdk.org/jeps/527)), so Java clients get it by moving to JDK 27 or later. Until then, machine clients on the ingest host stay classical.

## Crypto agility (decided)

Decided: every signed or sealed format names its algorithm, so a hybrid scheme can arrive without a flag day. The registry and byte layouts are in [verification.md](./verification.md#algorithm-identifiers); the service registry is `Algorithms.java`, and migration `V19__algorithm_identifiers.sql` replaced the bare-hex formats (no real data existed).

- **Typed values.** Every key, signature, sealed box and access hash is `<identifier>:<hex>`, for example `ecdsa-p256-sha256:04…` or `ecies-p256-hkdf-sha256-aes256gcm:04…`. The hybrid names are reserved now: `ecdsa-p256+ml-dsa-65`, `ecies-p256+ml-kem-768-hkdf-sha256-aes256gcm` and `threshold-elgamal-p256+ml-kem-768-hkdf-sha256-aes256gcm`.
- **Inside the signed bytes.** A signature signs `{"alg":"ecdsa-p256-sha256","hash":"<hex>"}`, so its identifier cannot be swapped. The signed definition carries typed keys and an `algorithms` object, so the operator signature covers every identifier the election uses. The access hash's identifier and Argon2id parameters are inside the device-signed ballot. A sealed box binds its identifier into the HKDF info.
- **No downgrade.** A verifier refuses an unknown or reserved identifier, one of the wrong kind, and a signature whose identifier differs from its key's. A key is checked only under the algorithm it was registered with, never one a message claims.
- **Access hashes.** Argon2id is written `argon2id-v13:m=65536,t=3,p=1:<hex>`, so its parameters travel with the hash. A second KDF, PBKDF2 for example, would be a new registry entry; none is added, and the FIPS question about Argon2id is still open.
- **Device voting.** The escrow (`threshold-elgamal-p256-hkdf-sha256-aes256gcm`) and blind-token (`rsabssa-sha384-psszero-deterministic`) formats in [device-voting.md](./device-voting.md) carry identifiers the same way, ready for the first implementation.
- Old elections keep verifying under their own identifiers, and a new election takes the suite current at activation.
