# Verifying a vote chain

Anyone can check a published chain without this repository. The [license](../LICENSE) lets a reviewer run the software in private for study. A real check uses published data and these rules. An SDK may be offered. A reviewer may also write their own program.

## Payload bytes

`Election.hashAlgorithm` is `SHA-256`. `Election.ballotHashPayloadSchema`, `voteHashPayloadSchema`, and `voteChainEntryHashPayloadSchema` are `canonical-json-v1`.

Each ballot, vote, and chain entry stores its canonical JSON text in `payload`. The integrity hash is SHA-256 of the UTF-8 bytes of that stored text. Do not base64-encode the JSON before hashing or storing it. Base64 is only how the access code is shown to the voter.

The columns and the JSON say the same thing. A verifier hashes the stored `payload` and checks that its fields match the columns. For a first chain entry the stored text is:

`{"voteHash":"abc","previousEntryHash":null,"index":0,"timestamp":"2026-11-03T14:05:01.123Z","nonce":"n-1"}`

The access code is not in any payload. `voterAccessHash` is a field inside the ballot payload. The selection itself (candidate or referendum value) is the vote payload. The ballot payload lists the hashes of those votes.

Canonical JSON for `canonical-json-v1`:

- UTF-8, no byte-order mark, no insignificant whitespace.
- Keys appear in the order listed below for that payload, not in alphabetical order. Every listed key is present. An absent reference is JSON `null`.
- Strings use JSON escaping: `"`, `\`, and control characters are escaped. `/` is not escaped. `\u` escapes use lowercase hex.
- UUIDs are lowercase `8-4-4-4-12` strings.
- Timestamps are UTC `YYYY-MM-DDTHH:mm:ss.sssZ`, with exactly three millisecond digits.
- Integers have no decimal point and no exponent.
- Booleans are `true` or `false`.
- The hash result stored in a column, and any hash embedded in a later payload, is lowercase hex.

Ballot keys, in order: `electionId`, `votingPrecinctId`, `type`, `recordingDeviceId`, `recordingPlaceId`, `recordedBy`, `timestamp`, `voterAccessHash`, `voteHashes`. `recordedBy` is the device principal for an in-person ballot and the staff principal for a mail-in or absentee ballot. `voteHashes` lists each of that ballot's vote hashes once. The array is sorted by the vote's race id, then by numeric rank ascending, then by `raceCandidateId`. A vote whose `value` is not a rank sorts as rank 0. `type` `absentee` follows the mail-in access-code rule.

Vote keys, in order: `ballotHash`, `raceCandidateId`, `value`.

`voteHashes` are the integrity hashes of those vote payloads. `ballotHash` inside each vote is the SHA-256 of this ballot's payload with `voteHashes` set to an empty array. It is not `Ballot.hash`. `Ballot.hash` covers the vote hashes, and the vote hashes cover `ballotHash`, so the two values cannot be equal. A verifier rebuilds that empty-array payload, hashes it, and checks that every vote payload names that hash.

Chain-entry keys, in order: `voteHash`, `previousEntryHash`, `index`, `timestamp`, `nonce`. `previousEntryHash` is `null` on the first entry. `index` is a number. `nonce` is a string of 1 to 64 characters, unique among entries on that chain. Verifiers check that bound and that uniqueness. They do not recompute the nonce.

## Access-code hash

The access code is 32 bytes from a cryptographic random generator. The voter sees it as base64url without padding. The QR code encodes that text. Lookup decodes the text back to the 32 bytes before hashing. The integrity hashes above include `voterAccessHash`. They do not include the access code.

Each access hash names its algorithm, as described in [Algorithm identifiers](#algorithm-identifiers). The hash is inside the device-signed ballot payload, so the device signature covers the identifier and the parameters.

- **In person.** `voterAccessHash` is `sha256:` then lowercase hex of SHA-256 over the 32 raw bytes.
- **Mail-in and absentee.** `voterAccessHash` is `argon2id-v13:m=65536,t=3,p=1:` then lowercase hex of Argon2id version 1.3 (version byte `0x13`) over the viewing passphrase, with the 32 raw code bytes as the salt. `m` is the memory cost in KiB (64 MiB), `t` the iterations and `p` the parallelism; the output is 32 bytes. The passphrase is trimmed, then Unicode NFC, then UTF-8. There is no secret key and no associated data. The passphrase is the one set at registration. It is not printed on the letter.

An in-person ballot must carry a `sha256:` hash and a mail-in or absentee ballot an `argon2id-v13:m=65536,t=3,p=1:` hash. Any other identifier or parameter set is refused on import and by the database. A future KDF, PBKDF2 for example, would be a new registry entry with its own prefix; none is accepted today.

The stored passphrase is an ECIES sealed box to the authority viewing public key. The column is `ecies-p256-hkdf-sha256-aes256gcm:` then lowercase hex of the box:

1. Pick an ephemeral P-256 key `e` and compute `E = e·G`, encoded as 65 bytes uncompressed SEC1.
2. `Z` is the 32-byte X coordinate of `e·V`, where `V` is the viewing public key's point (ECDH, SP 800-56A).
3. `okm` is 44 bytes of HKDF-SHA256 with an empty salt, input `Z`, and info `"ecies-p256-hkdf-sha256-aes256gcm" || 0x00 || E || V` (the ASCII identifier, one zero byte, then the two 65-byte points). The identifier is bound into the key, so a box cannot be relabelled as another algorithm and still open.
4. The first 32 bytes of `okm` are the AES-256-GCM key and the last 12 are the nonce. A fresh `e` per seal keeps the nonce unique. There is no associated data.
5. The box is `E || ciphertext || tag` (16-byte tag), so it is 81 bytes longer than the passphrase.

A box with any other identifier is refused before unsealing. The recording component unseals it with the viewing private key, which is the PIV ECDH operation on `E` when the key is on a YubiKey. Verifiers of the public chain do not unseal it. The fixed vector is in [Reference vectors](#reference-vectors).

## Election definition

The election payload keys, in order, are `electionId`, `hashAlgorithm`, `ballotHashPayloadSchema`, `voteHashPayloadSchema`, `voteChainEntryHashPayloadSchema`, `algorithms`, `operatorPublicKey`, `importPublicKey`, `viewingPublicKey`, `devices`, `races`, `ballotStyle`, `precinctDistricts`, `precinctLocalities`, `localityParents`.

`algorithms` is the object `{"signature":"ecdsa-p256-sha256","viewingBox":"ecies-p256-hkdf-sha256-aes256gcm","inPersonAccessHash":"sha256","mailAccessHash":"argon2id-v13:m=65536,t=3,p=1"}`, in that key order. It names every algorithm the election uses, inside the operator-signed bytes. A verifier refuses a definition whose `algorithms` differs from that object, and checks that every key, signature, box and access hash in the election carries the matching identifier. The three keys are typed too: `operatorPublicKey` and `importPublicKey` start `ecdsa-p256-sha256:` and `viewingPublicKey` starts `ecies-p256-hkdf-sha256-aes256gcm:`.

`devices` is an array of `{ "deviceId", "publicKey" }` sorted by `deviceId`, for every device that may sign ballots in this election.

`races` is an array sorted by `raceId`. Each object uses the keys `raceId`, `method`, `type`, `appointments`, `candidates`, in that order. `method` is `first-past-the-post`, `referendum`, or `ranked-choice`. `type` is the race type, including `primary` when the party rule applies. `appointments` is the seat's number of winners. `candidates` is sorted by `raceCandidateId`. Each candidate uses `raceCandidateId`, `partyAffiliation`, `writeIn`, `referendumChoice`. `partyAffiliation` and `referendumChoice` are JSON `null` when empty. `referendumChoice` is `yes` or `no` on a referendum. `writeIn` is true only for the single write-in slot on a first-past-the-post race.

`ballotStyle` is an array of `{ "votingPrecinctId", "raceId" }` sorted by precinct id, then race id.

`precinctDistricts` is an array of `{ "votingPrecinctId", "districtType", "votingDistrictId" }` sorted by precinct id, district type, then district id. Every precinct in the ballot style has exactly one row for each district type that appears on any of them.

`precinctLocalities` is an array of `{ "votingPrecinctId", "localityId" }` sorted by precinct id. `localityId` is the locality of the precinct's address.

`localityParents` is an array of `{ "localityId", "parentLocalityId" }` sorted by locality id. `parentLocalityId` is `null` on a root. The array includes every precinct locality and every parent up through the election's locality.

`Election.hash` is SHA-256 of that JSON. `Election.signature` is the operator signature over that hash, as in [Signatures](#signatures).

An empty race chain has no entry hash. Its head payload keys are `raceId`, `length`, and `length` is 0. `VoteChain.hash` is SHA-256 of that JSON. A non-empty chain's `hash` remains the latest entry's hash. That is the head: the tip, as in git's `HEAD`. The first entry is the genesis entry. In both cases the operator signature covers `VoteChain.hash`, as in [Signatures](#signatures). A closed chain has both `hash` and `signature` set.

## Algorithm identifiers

Every key, signature, sealed box and access hash is written `<identifier>:<value>`: the identifier, one colon, then the value in lowercase hex (an access hash adds its parameters before the hex). The identifiers are:

| Identifier | Kind | Status | Used for |
| --- | --- | --- | --- |
| `ecdsa-p256-sha256` | signature | active | every signature and signing public key below |
| `ecies-p256-hkdf-sha256-aes256gcm` | sealed box | active | the viewing public key and sealed viewing passphrases |
| `sha256` | access hash | active | in-person `voterAccessHash` |
| `argon2id-v13` | access hash | active | mail-in and absentee `voterAccessHash`, always with `m=65536,t=3,p=1` |
| `threshold-elgamal-p256-hkdf-sha256-aes256gcm` | escrow | active, device voting only | escrow ciphertexts in [device-voting.md](./device-voting.md) |
| `rsabssa-sha384-psszero-deterministic` | blind token | active, device voting only | eligibility tokens in [device-voting.md](./device-voting.md) |
| `ecdsa-p256+ml-dsa-65` | signature | reserved | the hybrid signature in [post-quantum.md](./post-quantum.md) |
| `ecies-p256+ml-kem-768-hkdf-sha256-aes256gcm` | sealed box | reserved | the hybrid sealed box |
| `threshold-elgamal-p256+ml-kem-768-hkdf-sha256-aes256gcm` | escrow | reserved | the hybrid escrow |

Device voting is not built, so the escrow and blind-token identifiers have no code yet; their formats are fixed in device-voting.md so the first implementation uses them. The service registry is `Algorithms.java`.

A verifier refuses:

- a value with no identifier, or one not in the table;
- a reserved identifier, until a later version of this document defines it;
- an identifier of the wrong kind for its place, such as a sealed-box key where a signing key belongs;
- a signature whose identifier differs from its key's identifier;
- a definition whose `algorithms` object differs from the one in [Election definition](#election-definition).

An identifier cannot be swapped after signing. Signatures carry it inside the signed message, the definition signature covers every key's identifier and the `algorithms` object, a device signature covers the ballot's access-hash identifier and parameters, and a sealed box binds its identifier into the HKDF info.

## Signatures

- **Curve and hash.** `ecdsa-p256-sha256`: ECDSA on NIST P-256 (secp256r1) with SHA-256 (FIPS 186-5).
- **Public key.** `ecdsa-p256-sha256:` then uncompressed SEC1 `04 || X || Y`, 65 bytes, 130 hex characters. The point must be on the curve. Compressed points and SPKI DER are refused.
- **Signature.** `ecdsa-p256-sha256:` then IEEE P1363 `r || s`, each a 32-byte big-endian integer, 64 bytes, 128 hex characters. DER is refused.
- **Low S.** `s` must be at most `n / 2`, where `n` is the group order (`ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551`). A signer that gets a high `s` replaces it with `n - s`. A verifier refuses a high `s`, so each signature has one valid encoding and cannot be altered without the key. `r` and `s` must be between 1 and `n - 1`.
- **What is signed.** Every signature in this document signs a 32-byte SHA-256 hash `h` (an integrity hash, a head, a bundle hash or a request hash). The signed message is the UTF-8 statement `{"alg":"ecdsa-p256-sha256","hash":"<h as 64 lowercase hex>"}`, with no spaces, and the signature is standard ECDSA-SHA256 over those bytes. So `SHA256withECDSAinP1363Format` in Java, `ec.ECDSA(hashes.SHA256())` in pyca/cryptography, or `openssl dgst -sha256 -verify` checks it against the statement. A signature over the bare hash, the old format, does not verify.
- P-256 signatures and sealed boxes do not survive a large quantum computer. [post-quantum.md](./post-quantum.md) describes the exposure and the hybrid identifiers reserved above.

- A ballot or vote signature verifies under `recordingDevice.publicKey`, and that device is in the signed `devices` list.
- A chain-entry signature verifies under `Election.importPublicKey`.
- A published chain head, the election definition, and the canvass statement verify under `Election.operatorPublicKey`.
- A validation result verifies under that validator's own `publicKey`.

A validation result stores its own canonical `payload`. The keys, in order, are `voteChainId`, `validatorId`, `validatedThroughHash`, `length`, `result`, `dateValidated`, `notes`, `evidenceHash`. `result` is `valid`, `invalid`, or `inconclusive`. `notes` and `evidenceHash` are `null` when absent. `evidenceHash` is lowercase hex SHA-256 of the evidence file bytes. The result's integrity hash is SHA-256 of that payload, and the validator signature covers that hash. The signed fields are immutable after insert. A validation result does not change a tally and does not block certification. The operator-signed head is the published result. A dissenting result stays public beside it.

The canvass statement is one canonical payload for the election. Its keys, in order, are `electionId`, `races`, `places`.

`races` is sorted by `raceId`. Each race uses `raceId`, `chainHash`, `length`, `precincts`, `districts`, `localities`, `results`. `chainHash` equals that race's closed `VoteChain.hash`. Every precinct that includes the race in the signed ballot style appears, including precincts with zero ballots.

A precinct object uses `votingPrecinctId`, `ballots`, `undervotes`, `candidates`, sorted by precinct id. `ballots` is the number of ballots with that precinct. `undervotes` is how many of those ballots have no vote for this race. `candidates` is sorted by `raceCandidateId` and lists every candidate on the race. Each uses `raceCandidateId`, `votes`, `writeIns`. `writeIns` is sorted by `value` and each item uses `value`, `votes`. It is empty when the candidate is not a write-in. For a write-in candidate, `votes` is the sum of the `writeIns` counts. For ranked choice, `votes` counts only values of `1`. Otherwise `votes` counts the vote rows for that candidate.

A district object uses `districtType`, `votingDistrictId`, `ballots`, `undervotes`, `candidates`. Districts are sorted by district type, then district id. A precinct contributes to the one district of that type named in the signed `precinctDistricts`. For each district type, the sum of `ballots` across that type equals the race's ballots. Candidate objects match the precinct shape.

A locality object uses `localityId`, `ballots`, `undervotes`, `candidates`, sorted by locality id. A precinct counts toward a locality when its signed precinct locality is that locality or a descendant through `localityParents`, stopping at the election's locality. Localities above the election locality are omitted. The election locality's `ballots` equals the number of ballots whose precinct includes the race.

`results` is sorted by `raceCandidateId`. Each uses `raceCandidateId`, `votes`, `rank`, `outcome`. `votes` is the race-wide count, using the same meaning as a precinct `votes`. `rank` is the number 1 for a winner and `null` otherwise. `outcome` is `won`, `lost`, `eliminated`, or `tied`. The counting rules are in [system.md](./system.md).

`places` is sorted by `recordingPlaceId`. Each uses `recordingPlaceId`, `acceptedIssuances`, `ballots`. A place appears when either count is greater than zero. `acceptedIssuances` counts `BallotIssuance` rows with status `issued` or `received`. The field keeps its name. Voided issuances are omitted. The two counts may differ.

`CanvassStatement.hash` is SHA-256 of that payload. `CanvassStatement.signature` is the operator signature over that hash.

A restore incident, when present, uses the payload in [schema.md](./schema.md). Its hash is SHA-256 of that JSON, and the operator signature covers that hash. The restored heads in the incident are the chain heads the verifier is checking. The witnessed heads are the latest outside validator heads from before the restore. The incident does not remove entries. It records that the promoted chain and the last outside witness differ.

## Close bundle

After close, the operator signs one bundle per election, and `GET /elections/{id}/close-bundle` serves its exact bytes. The `SureVote-Bundle-Hash` header is SHA-256 of the body, and `SureVote-Bundle-Signature` is the typed operator signature over that hash. The closing head notice carries the same value as `bundleHash`. The bundle is canonical JSON. Its keys, in order, are `electionId`, `definitionHash`, `definitionSignature`, `chains`, `entries`, `ballots`, `votes`, `canvass`, `restoreIncident`.

- `chains` is sorted by `raceId`. Each uses `raceId`, `voteChainId`, `length`, `hash`, `signature`.
- `entries` is sorted by `raceId`, then `index`. Each uses `voteChainId`, `payload`, `hash`, `signature`.
- `ballots` and `votes` are each sorted by `hash`, so their order does not show recording order. Each uses `payload`, `hash`, `signature`.
- `canvass` uses `payload`, `hash`, `signature`.
- `restoreIncident` is `null` or uses `payload`, `hash`, `signature`.

Each `payload` is the stored canonical text as a JSON string, so a verifier hashes the decoded string, not the escaped form. A signature that is absent is `null`. The bundle has no access codes, viewing passphrases, voter rows, or issuance rows. A verifier can run every check below from the bundle and the signed definition.

## Request signatures

A signed `POST /ballots` or `POST /elections/{id}/validations` is authenticated separately from the ledger signatures above. The request hash is SHA-256 of the UTF-8 text `method\npath\nkeyId\ntimestamp\nnonce\nbodyHash`, where `path` is the raw request path and `bodyHash` is lowercase hex SHA-256 of the body bytes. The signature is the typed `ecdsa-p256-sha256` signature over that hash, made as in [Signatures](#signatures), in the `SureVote-Signature` header. It is not stored on a ledger row, and a verifier does not need it.

## Reference vectors

[vectors/generate.py](./vectors/generate.py) computes the formats above with pyca/cryptography (OpenSSL) and argon2-cffi, which share no code with the service, and [vectors/vectors.json](./vectors/vectors.json) is its output. `CryptoRulesTest` checks the service against those values. To reproduce them:

```sh
python3 -m venv /tmp/sv && /tmp/sv/bin/pip install cryptography argon2-cffi
/tmp/sv/bin/python docs/vectors/generate.py | diff - docs/vectors/vectors.json
```

The vectors use the private scalars `11` and `22` repeated 32 times as the viewing (or signing) key and the ephemeral key, the hash `ab4e9d99…f48c0c`, the passphrase `correct horse battery` with the salt `33` repeated, and the in-person code `44` repeated. The reference signature is RFC 6979 deterministic ECDSA, so every value is deterministic and the `diff` prints nothing. The service itself signs with random nonces; either kind verifies the same way.

## Checks

A verifier does the following.

1. Fetch the signed election definition, including devices, races, ballot style, precinct districts, precinct localities, and locality parents.
2. Fetch the public ledger for that election: ballots, votes, chain headers, chain entries, any restore incident, and, when the election is `historical`, the canvass statement.
3. Hash the stored `payload` of each vote, ballot, chain entry, validation result, canvass statement, and any restore incident, and confirm it equals the stored integrity hash. Confirm each ballot, vote, chain-entry, and validation-result payload field matches its column. The canvass payload is checked by recomputing it in step 8.
4. Confirm each entry's `previousEntry` and `index` form one chain per race. Confirm each vote has exactly one entry, on the chain for that vote's race. Confirm a non-empty header `hash` equals the latest entry's hash, and an empty header `hash` equals the hash of the empty-head payload.
5. Confirm every identifier as in [Algorithm identifiers](#algorithm-identifiers). Confirm the election-definition signature, each device signature, each import signature, each published head signature, each validator signature, and the canvass signature with the keys in [Signatures](#signatures).
6. Confirm every vote's race is in the signed ballot style for that ballot's precinct, and that the ballot meets the selection rules in [system.md](./system.md). Confirm `voteHashes` matches that ballot's vote hashes in the sort order above. Confirm each vote's `ballotHash` as defined above. Confirm `Ballot.timestamp` is inside the election's voting window, inclusive.
7. Recompute `votesReceived`, `rank`, and `outcome` from the chain and compare them to the published caches.
8. When the canvass statement is published, recompute it from the chains and the signed definition and confirm it matches the stored payload.
9. If a restore incident exists, check its operator signature and report the witnessed heads beside the restored heads.
10. For one voter: compute `voterAccessHash` from the access code, and from the viewing passphrase when the ballot is mail-in or absentee. Find that ballot, and show that its entries sit on the chain checked above.

A recorded ballot is final. The verifier does not drop ballots because a later ballot points at them. There is no such pointer.

The check shows that the published ledger still matches its hashes and its tally. It does not show that every ballot came from an eligible voter, and it does not show that the voter intended the selections on the ballot. Those limits are described in [system.md](./system.md).
