# Voting from the voter's device

Status: design only. Nothing here is built. This is decision 3 in [system.md](./system.md#open-decisions): a voter casts a binding ballot from their own phone through a React Native app. The mail and in-person paths do not change and keep their `BallotIssuance` rows.

Decided: device voting **requires** a blind-signed one-time token. Also decided: the token is derived from a 12-word recovery phrase and kept in secure device storage, and the server re-signs only the same blinded message, so a voter can never hold two tokens ([Recovery phrase](#recovery-phrase)); a voter who turns up in person surrenders the token or casts a provisional ballot ([Voting in person after a device issuance](#voting-in-person-after-a-device-issuance)); issuance opens a configurable number of days before voting and closes when polls close ([Issuance window](#issuance-window)), and there is one RFC 9474 key per ballot style per election ([Keys](#keys)). The voter signs in only to get that token. The ballot is cast later, separately, with the token and nothing that names the voter or the device. Without the token there is no device channel.

## Goals

- One device ballot per eligible voter, enforced by the issuer, with no record anywhere of which ballot is whose.
- A cast request that carries no session, cookie, account, device id, push token, or advertising id. The only proof is the token.
- Anyone can check from the close bundle that every device ballot is matched by a distinct valid spent token in its style, and that no style spent more tokens than it issued, without any list linking a token to a ballot.
- The voter can open their own ballot with an access code, as in person.
- The app is an ordinary client of public rules. It holds no authority key.

Not goals for the first version: coercion resistance, re-voting, and voting without a network.

## Parties

| Party | Knows | Must not learn |
| --- | --- | --- |
| Voter's app | the voter's passkey, the token secret, the selections, the access code | nothing it does not already hold |
| Issuer (`issue.` host) | who signed in, their ballot style, when a token was issued | the token secret, the unblinded signature, the ballot |
| Casting endpoint (`cast.` host) | the token, the ballot, the arrival time | who the voter is, the session, the device |
| Host operator | both sides' logs, if it keeps them | a link between them; the design keeps the logs apart and the timing loose so that link is weak, not impossible |
| Election trustees (escrow, if adopted) | one voter's token, when k of n jointly open it during provisional adjudication | any other voter's token, and any ballot |
| Validators and the public | the close bundle, issued counts per style | anything about the voter |

The host is not trusted to forget. The design limits what it can correlate. It cannot make the host blind to a determined timing attack on a small style; see [Privacy limits](#privacy-limits).

## Enrollment and sign-in

The voter needs a credential the issuer trusts before the window opens.

1. The voter is on the roll (`Person`, `VoterRegistration`) with device voting allowed for that election.
2. The authority sends a one-time enrollment code by a channel it already uses for that voter: the registration address by mail, or in person at the clerk. The code is short-lived and single-use, like an official's enrollment token.
3. In the app, the voter enters the code and registers a passkey with the tenant's relying party. The issuer stores the WebAuthn credential on the voter, the same way `Credential` works for officials today.
4. At issuance time the voter signs in with that passkey. The issuer keeps a server-side session for that sign-in only. It never reaches the casting host.

A lost phone before issuance is a new enrollment code from the clerk, which revokes the old passkey.

## Keys

- **Token issuing key.** Decided: one RSA blind-signature key per ballot style per election, RSA-3072 with `RSABSSA-SHA384-PSSZERO-Deterministic` from RFC 9474 (see [Deriving the token](#deriving-the-token)). Why one per style:
  - The token has to bind to the voter's ballot style, so the casting endpoint can refuse a ballot for races the voter may not vote in, without learning who the voter is. The key that signed the token is that binding.
  - RFC 9474 signs a fully blind message and cannot carry public metadata. A single key per election would need partially blind signatures (`draft-irtf-cfrg-partially-blind-rsa`), which is not yet an RFC and has fewer reviewed implementations.
  - The style is already visible on the ballot: the payload names the precinct, and the races it holds show the style. A per-style key therefore narrows the anonymity set no further than the ballot itself does.
  - Small styles are still a risk, because the style alone can come close to naming a voter. See [Small styles](#small-styles).
  - Lifecycle: the public keys are listed in the election definition, so the operator signature publishes and freezes them at activate, before any issuance window opens. The private keys sign on demand only while issuance is open, in an HSM that can do raw RSA on a blinded message, such as AWS CloudHSM or a YubiHSM 2. AWS KMS signs only whole messages and cannot do this. After polls close the keys sign nothing. After certification (the signed close), each private key is destroyed, or archived offline under the two-officer rule when the authority's retention rules require it. The public keys stay in the definition for good.
- **Voter passkey.** Ordinary WebAuthn, used only at the issuer.
- **Recovery seed.** 128 random bits generated in the app, shown once as a 12-word BIP39-style phrase. Everything about the voter's token is derived from it, so the phrase is the token's backup. See [Recovery phrase](#recovery-phrase).
- **Trustee escrow key.** A threshold ElGamal key (k-of-n) held by the election trustees, generated by a distributed key generation so no single trustee ever holds the private key. Its public key is in the signed definition. Proposed, not decided; see [Escrow](#escrow-for-provisional-adjudication).
- **Access code.** 32 random bytes generated in the app at cast, with `voterAccessHash` computed in the app as for in person (SHA-256 over the raw bytes; see [verification.md](./verification.md)). The code is never sent.
- No device key. A per-device signing key would be a device id on every ballot.

## Deriving the token

The signature scheme is `RSABSSA-SHA384-PSSZERO-Deterministic` from RFC 9474: PSS with a zero-length salt and no random message prefix. Raw RSA on a blinded message is already deterministic; the zero salt and the missing prefix make the *client* side deterministic too, so the same seed always produces the same blinded message. RFC 9474 allows the deterministic variant when the message itself carries enough entropy, and here it carries 256 derived bits.

For an election `E` and style key `K`:

```
prk     = HKDF-Extract(salt = "SureVote-device-seed-v1", ikm = seed)
t       = HKDF-Expand(prk, "SureVote-token-v1"  || E || K, 32)
m       = "SureVote-device-token-v1" || E || K || t
tokenId = SHA-256(m)
r       = first value of HKDF-Expand(prk, "SureVote-blind-v1" || E || K || counter, len(n) + 16) mod n
          that is coprime to n, counting from counter = 0
blinded = Blind(K, m, r)
```

`E` and `K` are the 16-byte election id and the style key id. The labels separate every use of the seed, so one phrase can serve later elections without any value repeating across them.

## Getting a token

1. The app signs in at `https://issue.<host>` with the passkey.
2. The issuer checks the voter is eligible, the election is `active`, now is inside the voter's [issuance window](#issuance-window), and the voter has no accepted `BallotIssuance` for the election. It answers with the voter's ballot style key id. A voter with no party in a primary picks a slate here, which fixes the style.
3. If the app has no seed yet, it generates one and shows the [recovery phrase](#recovery-phrase) before going on. It derives `m`, `r`, and `blinded`, and sends only `blinded`, plus the escrow ciphertext when escrow is on.
4. In one transaction the issuer writes a `BallotIssuance` with channel `device`, status `issued`, the style key id, `blinded`, and the escrow ciphertext, then signs `blinded`. If the transaction fails, nothing is signed. A voter who already has a device issuance gets only the [re-issuance](#re-issuance) path.
5. The app unblinds the signature to `s`, checks `s` verifies on `m`, and keeps the seed and `s` in the platform's secure storage (iOS Keychain with this-device-only access, Android Keystore-backed encrypted storage), excluded from cloud backup. `m` and `r` are recomputed from the seed when needed. The sign-in session ends. The app drops its cookies for the issuer host.

`blinded` is safe to keep next to the voter. Without `r`, which only the seed yields, it is uniformly random and cannot be matched to `m`, `tokenId`, or `s`.

The device `BallotIssuance` is never marked `received`. The issuer does not know when, or whether, the token is spent, and must not. It is the issuer's spend of that voter's eligibility. Mail and in-person paths see an accepted row and refuse a regular ballot; see [Voting in person after a device issuance](#voting-in-person-after-a-device-issuance).

## Recovery phrase

Decided: secure device storage, plus a wallet-style recovery phrase.

- The app shows the 12 words once, at the first issuance, and asks the voter to write them down and re-enter three of them. The app never shows them again, never puts them in a screenshot-able view without warning, and never sends them anywhere. Nothing SureVote prints or emails carries the phrase or the token.
- The warnings, on that screen and wherever the token is obtained: "These 12 words are the key to your ballot. Keep them like cash: someone who has them and this phone can cast it before you do. SureVote does not keep a copy and cannot reset them. If you lose this phone and these words, your device ballot cannot be recovered; you can still vote with a provisional ballot in person."
- Losing the device but keeping the phrase is recoverable. Losing both is not: nothing on the server can rebuild `m` or `r`.

### Re-issuance

1. The voter, on a new device, gets a new enrollment code from the clerk and registers a new passkey (the old passkey went with the old phone), then signs in.
2. The voter enters the phrase. The app re-derives `blinded` and asks the issuer to sign it again.
3. The issuer re-signs only when the request is byte-identical to the `blinded` stored on the voter's device issuance. Raw RSA on the same input gives the same blind signature, which unblinds to the same `s`. A voter can therefore never hold two different valid tokens. A wrong phrase gives a different `blinded` and is refused.
4. Re-issuance is rate-limited per voter (for example three a day) and recorded as a private `BallotIssuanceEvent` (`reissued`, time). The authority can notify the voter through the enrollment channel, so a voter learns if someone else re-issued.
5. If the token was already spent, the re-issued token is refused at casting with "token already spent". That tells the voter someone used it.

### Threat notes

- Treat the phrase as a bearer secret, like the token itself. It derives `m` and `r`; turning them into a usable `s` also needs the blind signature, which lives only on the voter's phone and with the issuer, behind the voter's passkey. So the phrase alone is not quite a ballot, but the phrase plus the old phone, or plus a passkey enrolled by someone impersonating the voter at the clerk, is. Whoever casts first wins, and the voter cannot undo it. That is the same loss as a stolen mailed ballot, and the voter learns of it when their own cast or surrender is refused.
- The phrase alone also lets its holder compute `tokenId` and, after close, see in the public spent list whether and how that token was used (cast, surrendered, or revoked). It never shows the ballot.
- A phrase reused across elections exposes every future token derived from it. The per-election labels keep tokens apart, but one theft covers all of them until the voter rotates the seed, which they can do at any issuance before the first one of an election.
- The server stores `blinded`, the blind signature, and the escrow ciphertext. None of them link the voter to `tokenId` without `r` or the trustees.
- 128 bits of seed is beyond brute force; the 12-word checksum catches typos, not attackers.

## Voting in person after a device issuance

A voter with a device issuance who turns up in person must not end up with two counted ballots. There are two paths.

### Surrender

If the voter still has the app or the phrase, they surrender the token before voting in person, from the app ahead of time or at the poll worker's device.

1. The voter presents `m`, `r`, and `s` (the app derives them; at the polls the app shows them as a QR for the pollbook).
2. The pollbook, already signed in as the official handling this voter, sends them to the issuer. The issuer checks that `Blind(K, m, r)` equals the `blinded` on this voter's device issuance, so the token is this voter's and nobody else's, and that `s` verifies.
3. In one transaction it inserts `SpentToken(tokenId, reason = surrendered)`. The unique constraint makes surrender and a concurrent cast race safely: one of them wins. If the token was already spent, surrender fails and the voter is offered a provisional ballot.
4. On success the device issuance is marked `voided` with reason "token surrendered", and the voter is accepted on the pollbook as usual.

Surrender reveals `tokenId` next to the voter's identity. That is safe: it shows only that this token was never used, which says nothing about how anyone voted. The token is burned, so it can never be linked to a ballot later.

### Provisional ballot

If the phrase and the device are both gone, the voter casts the jurisdiction's provisional ballot. Deciding it needs to know whether the voter's token was spent.

#### Escrow for provisional adjudication

Proposed, not decided.

- At issuance the app encrypts `(m, r)` for this election to the trustee escrow key and sends the ciphertext with `blinded`. The server stores it on the device issuance. Escrowing `(m, r)` rather than `tokenId` is what makes it checkable: an app that escrowed a made-up id could cast its real token and then claim a lost one. Escrowing the seed would expose the voter's later elections too, so it is not escrowed.
- During provisional adjudication, after device casting has closed, k of the n trustees jointly decrypt that one voter's ciphertext. They check `Blind(K, m, r)` equals the stored `blinded`. A mismatch means the app escrowed garbage, and the provisional ballot is rejected.
- They look up `tokenId = SHA-256(m)` in the spent list:
  - not spent: insert `SpentToken(tokenId, reason = revoked)` and count the provisional ballot;
  - spent (cast or surrendered): reject the provisional ballot.
- Every decryption is published to the ledger as an `EscrowOpening` (election, `tokenId`, outcome, trustee signatures), without the voter's name. The count of openings is public, so an opening without a matching provisional ballot is visible.

**Requirement.** Escrow is safe only if the spent list can never be joined to a ballot. If it could, opening a voter's escrow would reveal their vote. So:

- `SpentToken` and the ballot tables share no key and no reference: no `ballotId`, no ballot hash, no token data on the ballot.
- `SpentToken` carries no timestamp, not even a truncated one, and its rows are published sorted by `tokenId`.
- The cast writes the spent row and hands the ballot to a separate pending store in a separate step (below), so no transaction id or commit order joins them in the database or its write-ahead log.
- Device ballots reach the chain only in shuffled batches, after the random delay, with hour-truncated timestamps.

Tradeoffs:

- Trustees can decrypt more than they should. That is why it is k-of-n with a distributed key, why every opening is published, and why each ciphertext covers one voter and one election.
- The host runs the casting endpoint. A host that instruments it live sees which token arrived with which ballot. Without escrow that link reveals nothing about the voter, because the token is blind. With escrow, a malicious host plus one opening reveals that voter's vote, and a malicious host plus k colluding trustees could reveal every device voter's vote. The separation above limits what is *stored*; it does not stop a live attacker who controls both.
- Without escrow: the jurisdiction's existing provisional-ballot rules decide, using the device issuance, the voter's statement, and the style's issued and spent counts at close. If every token in the style was spent, the provisional is a likely second ballot; otherwise it is not decidable for one voter. No vote can ever be revealed this way, at the cost of either counting some double votes or rejecting some honest provisionals. This is the same position as a mailed ballot the voter says never arrived.

### Closing order

Device casting closes at `votingEndDate`, with issuance. Provisional adjudication and every escrow opening happen after that. A token can therefore never be spent after it was revoked, because nothing is accepted once casting has closed.

A token is spent when the casting endpoint accepts the cast, which is when the `SpentToken(reason = cast)` row commits. It is not spent when the app schedules the cast, and not when the ballot is later appended to the chain. A cast still waiting out its random delay in the app is not spent. The app sends before a safety margin ahead of polls close, and the casting endpoint refuses anything after `votingEndDate`, so a delayed cast either lands in time or never counts, and the voter sees which.

## Issuance window

Decided: issuance opens a configurable number of days before voting starts and closes when polls close.

- `Election.deviceTokenLeadDays` (Integer, optional) is the default. Empty means device voting is off for that election. The window opens at `votingStartDate` minus that many days and closes at `votingEndDate`, the existing polls-close instant.
- An override can be set per district or per precinct. The most specific one wins: precinct, then district, then election.
  - Precinct: `ElectionVotingPrecinctAssignment.deviceTokenLeadDays`, on the row that already ties a precinct to the election.
  - District: a new `ElectionDistrictSetting` row (`election`, `votingDistrict`, `deviceTokenLeadDays`), unique on (`election`, `votingDistrict`). A precinct reaches its districts through `PrecinctDistrict`.
  - A precinct in two districts with different overrides and no precinct override is ambiguous. The service refuses to activate until a precinct override settles it.
- The resolved opening for each precinct goes into the signed election definition, so validators and voters see the same window the issuer enforces. Changing it after activate is not possible, like every other part of the definition.
- Issuance needs the election `active`, because the style keys are only published by the signed definition. Officials therefore activate before the earliest opening. Ballots are still refused before `votingStartDate`: a token fetched early is held in the app until voting starts, and the random delay below runs from the later of issuance and voting start.
- Casting closes at `votingEndDate` too. A token that is not spent by then is never spent.

Opening earlier widens the anonymity set for timing, because more tokens are outstanding when the first device ballots arrive.

## Casting

1. The voter fills in the ballot in the app from the signed definition, which the app fetched and verified against the operator key. The app enforces the style and the rules in [What may be on a ballot](./system.md#what-may-be-on-a-ballot).
2. The app waits a random delay before sending. It schedules the cast for a uniformly random time between 10 and 60 minutes after the later of token issuance and `votingStartDate`, but never later than a safety margin before polls close. If the voter casts more than an hour after issuance, the delay already exists and the app sends at once. The voter sees "your ballot will be sent at about 14:20" and can leave the app; the cast runs as a background task, and runs at next open if the OS killed it.
3. The app sends `POST https://cast.<host>/device-ballots` with the ballot payload, the vote payloads, `m`, `s`, and `styleKeyId`. The request uses a separate HTTP client: no cookie jar, no `Authorization`, no custom headers beyond `Content-Type`, a generic user agent, and no TLS session resumption shared with the issuer host. It goes through an Oblivious HTTP relay (RFC 9458) run by a party other than the host when one is available, so the casting host sees the relay's address, not the voter's.
4. The casting endpoint verifies `s` on `m` under the named style key, and that the key belongs to this election and casting is open. It commits `SpentToken(electionId, keyId, tokenId, m, s, reason = cast)`, unique on `(electionId, tokenId)`. A second cast with the same token is refused with "token already spent".
5. Only after that commit, it hands the ballot and its votes to a pending store outside the ledger database, in the same way the received queue keeps issuance ids outside it. The pending ballot carries no token data. If the hand-off fails, the endpoint retries it; the token is already spent, so the ballot must not be dropped.
6. The response is the ballot hash and the endpoint's signature over it. The app then shows the access code once, exactly like the in-person slip, and wipes `m`, `r`, `s`, and the code from memory after the voter confirms they saved it. The seed stays, for later elections, until the voter removes it.

A lost response is not retried as a new cast: the retry gets "token already spent". The app instead looks the ballot up with its access code once it is on the chain. Pending ballots are searchable by `voterAccessHash` until imported, so the app can confirm sooner.

The authority's import workstation pulls pending device ballots in batches, checks each ballot against its style, shuffles the batch, and appends the chain entries with the import key, as it does for media from the polls. Batching keeps chain order from showing arrival order.

The ballot payload uses type `device`. `recordingDeviceId`, `recordingPlaceId`, and `recordedBy` are null. Its `timestamp` is set by the casting endpoint and truncated to the hour, so the ledger does not carry a millisecond arrival time. There is no device signature and no token data on the ballot.

The issuer never calls the casting host and the casting host never calls the issuer. They share the election definition and nothing else.

## Separate endpoints

- Different hostnames, certificates, load balancers, and WAF web ACLs. CloudFront logging stays off on both, as in `infra/`.
- The casting host keeps no IP or request log. It cannot rate-limit by IP for the same reason, and the relay would defeat that anyway. Its rate limit is the token: a request without a valid signature is rejected before any write, and signature checks are cheap enough to absorb abuse at the edge.
- The issuer logs sign-ins as it does for officials. Those logs never sit next to casting data.
- Separate database roles. The casting role can insert `SpentToken` and write the pending store, and cannot read `person`, `voter_registration`, `credential`, or `ballot_issuance`. The issuer role can insert surrendered and revoked `SpentToken` rows and cannot read ballots or the pending store. Both are still the host. This separation is what an honest host keeps; it is not a proof against a dishonest one.

## What validators can check

At close the bundle adds, as separate lists that share no key with the ballots:

- each style key and its issued count, from the device `BallotIssuance` rows grouped by style;
- every `SpentToken` (`keyId`, `m`, `s`, reason), sorted by `tokenId`;
- every `EscrowOpening`.

A validator checks every `s` under its key, that no `tokenId` repeats, that per style the `cast` rows equal the device ballots, and that cast plus surrendered plus revoked rows do not exceed issued tokens. Each revoked row must have a matching opening.

## Privacy limits

- **Timing.** The host sees issuance times and, truncated to the hour, cast times. The random delay, the hour truncation, and a relay make a match a guess among everyone of that style who got a token earlier. Early in the window, or in a small style, that guess can be good.
- <a id="small-styles"></a>**Small styles.** The anonymity set is per style, and a style with very few voters can be close to identifying, with or without device voting. Mitigations, chosen per election when the definition is built:
  - a minimum style size: device voting is off for a style whose eligible voters are below the threshold;
  - merging: a tiny precinct shares a style key and is reported with a neighboring precinct that has the same races, so its device ballots name the merged group, not the precinct;
  - flagging: the definition marks the style as small, the issuer warns the voter that their ballot may be identifiable, and the canvass statement lists the flagged styles.
- **The app is a trusted client.** It sees the selections, the seed, and the code. Open source and reproducible builds help. A voter can check the result by looking the ballot up on another device with the code.
- **Coercion.** Same as in person: the access code is a bearer token, and someone standing over the voter sees the ballot. The phrase adds a second bearer secret a coercer can demand. This design adds no coercion resistance.
- **Escrow.** If adopted, it adds the trustee and live-host risks in [Escrow](#escrow-for-provisional-adjudication).

## Data model changes (proposed, not built)

- `ElectionDeviceKey`: `electionId`, `ballotStyleKey` (the precinct, or merged precinct group, and party it covers), `keyId`, `publicKey`, `small` (Boolean), listed in the signed definition.
- `Election.deviceTokenLeadDays`, `ElectionVotingPrecinctAssignment.deviceTokenLeadDays`, and `ElectionDistrictSetting` for the [issuance window](#issuance-window). `Election.escrowPublicKey` and the trustee roster when escrow is on.
- `BallotIssuance.channel`: `in-person`, `mail`, `device`. A device row also has the style key id, `blindedMessage`, `blindSignature`, and `escrowCiphertext`, and no recording place.
- `BallotIssuanceEvent`: private, per issuance: `reissued`, `surrendered`, `escrow-opened`, with a time.
- `SpentToken`: `electionId`, `keyId`, `tokenId`, `message`, `signature`, `reason` (`cast`, `surrendered`, `revoked`). Insert-only, unique on `(electionId, tokenId)`. No ballot reference and no timestamp columns, so the usual ledger audit fields are left off this table on purpose. Published at close.
- `EscrowOpening`: `electionId`, `tokenId`, `outcome` (`revoked`, `spent`, `mismatch`), trustee signatures. Public, insert-only.
- A pending device-ballot store outside the ledger database.
- `BallotType.DEVICE`.
- Reconciliation gains a device row per style: issued tokens, cast, surrendered, revoked, and device ballots, matched when cast equals device ballots and the sum does not exceed issued.

## Open questions for Josh

Decided and moved into the sections above: the issuance window, one key per style, secure storage with a recovery phrase and byte-identical re-issuance, surrender before voting in person, and the closing order.

1. **Escrow.** Adopt trustee escrow for provisional adjudication, or leave provisionals after a lost phrase to the jurisdiction's existing rules? If adopted: who the trustees are, k and n, and whether the escrow key is per election or per tenant.
2. **Phrase scope.** One phrase per voter reused across elections (the current text), or a new phrase per election? Reuse is easier for voters; a per-election phrase limits a theft to one election.
3. **Re-issuance limits.** The rate limit, and whether the voter must be notified on every re-issuance.
4. **Relay.** Require an Oblivious HTTP relay run by an outside party, make it best-effort, or offer Tor as an option?
5. **Small-style threshold.** What minimum eligible voters per style, and which mitigation is the default: turn device voting off, merge, or flag?
6. **Delay bounds.** Is 10 to 60 minutes the right default, and should the voter be able to shorten it near the end of the window?
7. **Timestamp truncation.** Hour, or day? Day loses the ordering within a day that a validator might want for a witness gap.
8. **Enrollment.** Mailed code only, in person only, or both? Identity proofing beyond the roll is the authority's call; the system needs to know which it supports.
9. **Coercion and re-voting.** Accept bearer-code semantics for version one, or plan for a later scheme where a voter can recast and only the last counts?
10. **Import cadence.** How often the import workstation appends device batches during the window, and what minimum batch size it waits for. More often means fresher heads for validators; bigger batches hide arrival order better.
11. **HSM.** CloudHSM in the host account, or a YubiHSM 2 in the authority's custody connected to the issuer? The second fits the custody rules better but is harder to run online for a whole window.
12. **Ambiguous district overrides.** Refusing activation (the current design) or a fixed rule such as "the latest opening wins"?

## Not in this design

The app itself, the issuer and casting services, the token code, and the schema changes above. They are not started. No scaffold was added. The decided parts fix the token, key, and window shapes, but the relay, enrollment, and HSM questions still change the services' API and deployment.
