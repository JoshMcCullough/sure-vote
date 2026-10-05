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

Chain-entry keys, in order: `voteHash`, `previousEntryHash`, `index`, `timestamp`, `nonce`. `previousEntryHash` is `null` on the first entry. `index` is a number. `nonce` is a string of 1 to 64 characters, unique among entries on that chain. Verifiers check that bound and that uniqueness. They do not recompute the nonce.

## Access-code hash

The access code is 32 bytes from a cryptographic random generator. The voter sees it as base64url without padding. The QR code encodes that text. Lookup decodes the text back to the 32 bytes before hashing. The integrity hashes above include `voterAccessHash`. They do not include the access code.

- **In person.** `voterAccessHash` is lowercase hex of SHA-256 over the 32 raw bytes.
- **Mail-in and absentee.** `voterAccessHash` is lowercase hex of Argon2id version 1.3 (version byte `0x13`) over the viewing passphrase, with the 32 raw code bytes as the salt. The passphrase is trimmed, then Unicode NFC, then UTF-8. There is no secret key and no associated data. The memory cost is 65536 KiB (64 MiB), with 3 iterations, parallelism 1, and a 32-byte output. The passphrase is the one set at registration. It is not printed on the letter.

The stored passphrase is a libsodium `crypto_box_seal` ciphertext: an ephemeral X25519 key, then XSalsa20-Poly1305, sealed to the authority viewing public key. The column is lowercase hex of that ciphertext. The recording component unseals it with the viewing private key. Verifiers of the public chain do not unseal it.

## Election definition

The election payload keys, in order, are `electionId`, `hashAlgorithm`, `ballotHashPayloadSchema`, `voteHashPayloadSchema`, `voteChainEntryHashPayloadSchema`, `operatorPublicKey`, `importPublicKey`, `viewingPublicKey`, `devices`, `races`, `ballotStyle`, `precinctDistricts`, `precinctLocalities`, `localityParents`.

`devices` is an array of `{ "deviceId", "publicKey" }` sorted by `deviceId`, for every device that may sign ballots in this election.

`races` is an array sorted by `raceId`. Each object uses the keys `raceId`, `method`, `type`, `appointments`, `candidates`, in that order. `method` is `first-past-the-post`, `referendum`, or `ranked-choice`. `type` is the race type, including `primary` when the party rule applies. `appointments` is the seat's number of winners. `candidates` is sorted by `raceCandidateId`. Each candidate uses `raceCandidateId`, `partyAffiliation`, `writeIn`, `referendumChoice`. `partyAffiliation` and `referendumChoice` are JSON `null` when empty. `referendumChoice` is `yes` or `no` on a referendum. `writeIn` is true only for the single write-in slot on a first-past-the-post race.

`ballotStyle` is an array of `{ "votingPrecinctId", "raceId" }` sorted by precinct id, then race id.

`precinctDistricts` is an array of `{ "votingPrecinctId", "districtType", "votingDistrictId" }` sorted by precinct id, district type, then district id. Every precinct in the ballot style has exactly one row for each district type that appears on any of them.

`precinctLocalities` is an array of `{ "votingPrecinctId", "localityId" }` sorted by precinct id. `localityId` is the locality of the precinct's address.

`localityParents` is an array of `{ "localityId", "parentLocalityId" }` sorted by locality id. `parentLocalityId` is `null` on a root. The array includes every precinct locality and every parent up through the election's locality.

`Election.hash` is SHA-256 of that JSON. `Election.signature` is the operator signature over the raw hash bytes.

An empty race chain has no entry hash. Its head payload keys are `raceId`, `length`, and `length` is 0. `VoteChain.hash` is SHA-256 of that JSON. A non-empty chain's `hash` remains the latest entry's hash. In both cases the operator signature covers the 32 raw bytes of `VoteChain.hash`. A closed chain has both `hash` and `signature` set.

## Signatures

Public keys and signatures are lowercase hex. Signing keys are Ed25519. A signature is over the 32 raw bytes of the integrity hash, not over the hex text.

- A ballot or vote signature verifies under `recordingDevice.publicKey`, and that device is in the signed `devices` list.
- A chain-entry signature verifies under `Election.importPublicKey`.
- A published chain head, the election definition, and the canvass statement verify under `Election.operatorPublicKey`.
- A validation result verifies under that validator's own `publicKey`.

A validation result stores its own canonical `payload`. The keys, in order, are `voteChainId`, `validatorId`, `validatedThroughHash`, `length`, `result`, `dateValidated`, `notes`, `evidenceHash`. `result` is `valid`, `invalid`, or `inconclusive`. `notes` and `evidenceHash` are `null` when absent. `evidenceHash` is lowercase hex SHA-256 of the evidence file bytes. The result's integrity hash is SHA-256 of that payload, and the validator signature covers those raw bytes. The signed fields are immutable after insert. A validation result does not change a tally and does not block certification. The operator-signed head is the published result. A dissenting result stays public beside it.

The canvass statement is one canonical payload for the election. Its keys, in order, are `electionId`, `races`, `places`.

`races` is sorted by `raceId`. Each race uses `raceId`, `chainHash`, `length`, `precincts`, `districts`, `localities`, `results`. `chainHash` equals that race's closed `VoteChain.hash`. Every precinct that includes the race in the signed ballot style appears, including precincts with zero ballots.

A precinct object uses `votingPrecinctId`, `ballots`, `undervotes`, `candidates`, sorted by precinct id. `ballots` is the number of ballots with that precinct. `undervotes` is how many of those ballots have no vote for this race. `candidates` is sorted by `raceCandidateId` and lists every candidate on the race. Each uses `raceCandidateId`, `votes`, `writeIns`. `writeIns` is sorted by `value` and each item uses `value`, `votes`. It is empty when the candidate is not a write-in. For a write-in candidate, `votes` is the sum of the `writeIns` counts. For ranked choice, `votes` counts only values of `1`. Otherwise `votes` counts the vote rows for that candidate.

A district object uses `districtType`, `votingDistrictId`, `ballots`, `undervotes`, `candidates`. Districts are sorted by district type, then district id. A precinct contributes to the one district of that type named in the signed `precinctDistricts`. For each district type, the sum of `ballots` across that type equals the race's ballots. Candidate objects match the precinct shape.

A locality object uses `localityId`, `ballots`, `undervotes`, `candidates`, sorted by locality id. A precinct counts toward a locality when its signed precinct locality is that locality or a descendant through `localityParents`, stopping at the election's locality. Localities above the election locality are omitted. The election locality's `ballots` equals the number of ballots whose precinct includes the race.

`results` is sorted by `raceCandidateId`. Each uses `raceCandidateId`, `votes`, `rank`, `outcome`. `votes` is the race-wide count, using the same meaning as a precinct `votes`. `rank` is the number 1 for a winner and `null` otherwise. `outcome` is `won`, `lost`, `eliminated`, or `tied`. The counting rules are in [system.md](./system.md).

`places` is sorted by `recordingPlaceId`. Each uses `recordingPlaceId`, `acceptedIssuances`, `ballots`. A place appears when either count is greater than zero. `acceptedIssuances` counts `BallotIssuance` rows with status `accepted`. Voided issuances are omitted. The two counts may differ.

`CanvassStatement.hash` is SHA-256 of that payload. `CanvassStatement.signature` is the operator signature over the raw hash bytes.

A restore incident, when present, uses the payload in [schema.md](./schema.md). Its hash is SHA-256 of that JSON, and the operator signature covers the raw hash bytes. The restored heads in the incident are the chain heads the verifier is checking. The witnessed heads are the latest outside validator heads from before the restore. The incident does not remove entries. It records that the promoted chain and the last outside witness differ.

## Checks

A verifier does the following.

1. Fetch the signed election definition, including devices, races, ballot style, precinct districts, precinct localities, and locality parents.
2. Fetch the public ledger for that election: ballots, votes, chain headers, chain entries, any restore incident, and, when the election is `historical`, the canvass statement.
3. Hash the stored `payload` of each vote, ballot, chain entry, validation result, canvass statement, and any restore incident, and confirm it equals the stored integrity hash. Confirm each ballot, vote, chain-entry, and validation-result payload field matches its column. The canvass payload is checked by recomputing it in step 8.
4. Confirm each entry's `previousEntry` and `index` form one chain per race. Confirm each vote has exactly one entry, on the chain for that vote's race. Confirm a non-empty header `hash` equals the latest entry's hash, and an empty header `hash` equals the hash of the empty-head payload.
5. Confirm the election-definition signature, each device signature, each import signature, each published head signature, each validator signature, and the canvass signature with the keys in [Signatures](#signatures).
6. Confirm every vote's race is in the signed ballot style for that ballot's precinct, and that the ballot meets the selection rules in [system.md](./system.md). Confirm `voteHashes` matches that ballot's votes in the sort order above. Confirm `Ballot.timestamp` is inside the election's voting window, inclusive.
7. Recompute `votesReceived`, `rank`, and `outcome` from the chain and compare them to the published caches.
8. When the canvass statement is published, recompute it from the chains and the signed definition and confirm it matches the stored payload.
9. If a restore incident exists, check its operator signature and report the witnessed heads beside the restored heads.
10. For one voter: compute `voterAccessHash` from the access code, and from the viewing passphrase when the ballot is mail-in or absentee. Find that ballot, and show that its entries sit on the chain checked above.

A recorded ballot is final. The verifier does not drop ballots because a later ballot points at them. There is no such pointer.

The check shows that the published ledger still matches its hashes and its tally. It does not show that every ballot came from an eligible voter, and it does not show that the voter intended the selections on the ballot. Those limits are described in [system.md](./system.md).
