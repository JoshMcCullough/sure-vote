# Voting from the voter's device

Status: design only. Nothing here is built. This is decision 3 in [system.md](./system.md#open-decisions): a voter casts a binding ballot from their own phone through a React Native app. The mail and in-person paths do not change and keep their `BallotIssuance` rows.

Decided: device voting **requires** a blind-signed one-time token. Also decided:

- the token is derived from one 12-word recovery phrase per voter per tenant and kept in secure device storage; the server re-signs only the same blinded message, only when the voter asks, so a voter can never hold two tokens ([Recovery phrase](#recovery-phrase));
- a voter who turns up in person surrenders the token, or casts a provisional ballot that k-of-n officials decide through escrow ([Voting in person after a device issuance](#voting-in-person-after-a-device-issuance));
- issuance opens a configurable number of days before voting and closes when polls close ([Issuance window](#issuance-window));
- there is one RFC 9474 key per ballot style per election ([Keys](#keys));
- every timing and size choice is a setting with a default ([Settings](#settings)). The voter signs in only to get that token. The ballot is cast later, separately, with the token and nothing that names the voter or the device. Without the token there is no device channel.

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
| Escrow officials (k of n) | one voter's token, when k of n jointly open it during provisional adjudication | any other voter's token, and any ballot |
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
  - Lifecycle: the public keys are listed in the election definition, so the operator signature publishes and freezes them at activate, before any issuance window opens. The only later change is an operator-signed [key revocation](#signing-key-compromise). The private keys sign on demand only while issuance is open, in an HSM that can do raw RSA on a blinded message, such as AWS CloudHSM or a YubiHSM 2. AWS KMS signs only whole messages and cannot do this. After polls close the keys sign nothing. After certification (the signed close), each private key is destroyed, or archived offline under the two-officer rule when the authority's retention rules require it. The public keys stay in the definition for good.
- **Voter passkey.** Ordinary WebAuthn, used only at the issuer. It cannot be exposed by re-issuance or by any official, by design: its private key never leaves the voter's authenticator, and the issuer, and any official helping the voter, only ever see a one-time WebAuthn assertion bound to that sign-in's challenge. Nothing a passkey sign-in reveals lets anyone sign in again, so a passkey never needs replacing because of a recovery.
- **Recovery seed.** 128 random bits generated in the app, shown once as a 12-word BIP39-style phrase, one per voter per tenant. Every token of that voter in that tenant is derived from it, so the phrase is the tokens' backup. See [Recovery phrase](#recovery-phrase).
- **Escrow key.** A threshold ElGamal key (k-of-n), new for each election, held as shares by k-of-n election officials after a distributed key generation, so no official ever holds the whole private key. Its public key is in the signed definition. Shares are destroyed after certification. See [Escrow](#escrow).
- **Access code.** 32 random bytes generated in the app at cast, with `voterAccessHash` computed in the app as for in person (SHA-256 over the raw bytes; see [verification.md](./verification.md)). The code is never sent.
- No device key. A per-device signing key would be a device id on every ballot.

## Deriving the token

The signature scheme is `RSABSSA-SHA384-PSSZERO-Deterministic` from RFC 9474: PSS with a zero-length salt and no random message prefix. Raw RSA on a blinded message is already deterministic; the zero salt and the missing prefix make the *client* side deterministic too, so the same seed always produces the same blinded message. RFC 9474 allows the deterministic variant when the message itself carries enough entropy, and here it carries 256 derived bits.

For a tenant `T`, an election `E`, and style key `K`:

```
prk     = HKDF-Extract(salt = "SureVote-device-seed-v1" || T, ikm = seed)
t       = HKDF-Expand(prk, "SureVote-token-v1"  || E || K, 32)
m       = "SureVote-device-token-v1" || E || K || t
tokenId = SHA-256(m)
r       = first value of HKDF-Expand(prk, "SureVote-blind-v1" || E || K || counter, len(n) + 16) mod n
          that is coprime to n, counting from counter = 0
blinded = Blind(K, m, r)
```

`T` and `E` are the 16-byte tenant and election ids, and `K` is the style key id. The labels separate every use of the seed: one phrase serves all of a tenant's elections, and no derived value repeats across elections or styles, so tokens from different elections cannot be linked.

## Getting a token

1. The app signs in at `https://issue.<host>` with the passkey.
2. The issuer checks the voter is eligible, the election is `active`, now is inside the voter's [issuance window](#issuance-window), and the voter has no accepted `BallotIssuance` for the election. It answers with the voter's ballot style key id. A voter with no party in a primary picks a slate here, which fixes the style.
3. If the app has no seed for this tenant yet, it generates one and shows the [recovery phrase](#recovery-phrase) before going on. It derives `m`, `r`, and `blinded`, and sends only `blinded` and the [escrow](#escrow) ciphertext.
4. In one transaction the issuer writes a `BallotIssuance` with channel `device`, status `issued`, the style key id, `blinded`, and the escrow ciphertext, then signs `blinded`. If the transaction fails, nothing is signed. A voter who already has a device issuance gets only the [re-issuance](#re-issuance) path.
5. The app unblinds the signature to `s`, checks `s` verifies on `m`, and keeps the seed and `s` in the platform's secure storage (iOS Keychain with this-device-only access, Android Keystore-backed encrypted storage), excluded from cloud backup. `m` and `r` are recomputed from the seed when needed. The sign-in session ends. The app drops its cookies for the issuer host.

`blinded` is safe to keep next to the voter. Without `r`, which only the seed yields, it is uniformly random and cannot be matched to `m`, `tokenId`, or `s`.

The device `BallotIssuance` is never marked `received`. The issuer does not know when, or whether, the token is spent, and must not. It is the issuer's spend of that voter's eligibility. Mail and in-person paths see an accepted row and refuse a regular ballot; see [Voting in person after a device issuance](#voting-in-person-after-a-device-issuance).

## Recovery phrase

Decided: secure device storage, plus one wallet-style recovery phrase per voter per election authority (tenant).

- The app generates the seed and shows the 12 words once, at the voter's first issuance in that tenant, and asks the voter to write them down and re-enter three of them. The app never shows them again, warns before any view that could be screenshotted, and never sends them anywhere. Nothing SureVote prints or emails carries the phrase or the token.
- The same phrase serves every later election of that tenant. Each election's token is derived with its own HKDF labels over the tenant and election ids ([Deriving the token](#deriving-the-token)), so tokens, token ids, and blinded messages from different elections share no value and cannot be linked to each other. A different tenant gets a different phrase.
- The warnings, on that screen and wherever a token is obtained: "These 12 words are the key to your ballots with this election authority. Keep them like cash: someone who has them and this phone can cast your ballot before you do. SureVote does not keep a copy and cannot reset them. If you lose this phone and these words, a device ballot you have not cast cannot be recovered; you can still vote with a provisional ballot in person."
- Losing the device but keeping the phrase is recoverable. Losing both is not: nothing on the server can rebuild `m` or `r`.

### Rotating the phrase

The voter can rotate the phrase at any time, for example after it was exposed. Rotation needs a passkey sign-in, so a thief with only the phrase cannot rotate it.

1. The app generates a new seed and shows the new phrase with the same warnings and re-entry check.
2. For every active election in the tenant where the voter holds an unspent token derived from the old seed, the app [surrenders](#surrender) that token in the same session and then requests a new one derived from the new seed. A rotation in the middle of an election therefore always burns the old token. If surrender fails because the token was already cast, the voter keeps that cast ballot and gets no new token; if it fails because the old token is unknown to the app (device lost), the old token is revoked through [escrow](#escrow) instead.
3. The old seed is wiped. Tokens already cast stay cast. Later elections derive from the new seed.

The issuer records a `phrase-rotated` event and notifies the voter, like a re-issuance.

### Re-issuance

Decided: re-issuance happens only at the voter's request and the voter is always notified. The server never re-issues on its own. There is no case that needs it: every server-side event that affects a token (a style change, a key compromise, a provisional adjudication) revokes; only the voter can then ask for a new token.

1. The voter signs in with a passkey. On a new device that means a new enrollment code from the clerk first, because the old passkey went with the old phone.
2. The voter proves the token: by entering the phrase, or from the old device, which still holds the seed. The app re-derives `blinded` and asks the issuer to sign it again.
3. The issuer re-signs only when the request is byte-identical to the `blinded` stored on the voter's device issuance. Raw RSA on the same input gives the same blind signature, which unblinds to the same `s`. A voter can therefore never hold two different valid tokens. A wrong phrase gives a different `blinded` and is refused.
4. Re-issuance is rate-limited per voter by `deviceReissueLimitPerDay` ([Settings](#settings)) and recorded as a private `BallotIssuanceEvent` (`reissued`, time). The voter is notified every time, in the app and through the enrollment channel, so a voter learns if someone else re-issued.
5. If the token was already spent, the re-issued token is refused at casting with "token already spent". That tells the voter someone used it.

### Assisted recovery

Decided: an assisted recovery still happens in the voter's own app, so the phrase never reaches an official and no rotation is needed.

- The passkey is never at risk ([Keys](#keys)). The phrase is the only secret a recovery could expose.
- **Assisted.** The voter brings their own phone, old or new, to the clerk. The official only verifies the voter's identity and authorizes the re-issue, for example by issuing a new enrollment code. The voter enters the phrase in their own app on their own phone, and the re-issuance runs as in [Re-issuance](#re-issuance). The phrase never touches an official's or shared device, so the voter keeps it and nothing is rotated or surrendered.
- **Assisted-shared.** If the voter has no phone of their own, recovery on a shared or official device is allowed only when the tenant turns on `deviceSharedRecoveryAllowed` ([Settings](#settings), off by default). The flow is marked `assisted-shared`:
  - the device never persists the phrase or the seed: no storage, no logs, no crash reports, no clipboard, and memory is wiped when the flow ends;
  - the voter types the phrase on a masked screen or keypad that faces them, the official's screen shows only progress, and the flow asks the official to step away while it is entered;
  - at the end the app offers [rotation](#rotating-the-phrase) without forcing it, and explains that rotating replaces the phrase and, mid-election, the token.
- The re-issue, and any rotation the voter accepts, are recorded as `BallotIssuanceEvent`s with the `assisted` flag (`assisted` or `assisted-shared`), and the voter is notified. The issuer sets the flag from the request and whenever the request comes through an official's session.

### Ballot style changes

If the voter's style changes after a token was issued and before they cast, for example an approved precinct move, the old token must not stay usable.

1. Officials update the registration. The issuer marks the voter's device issuance `style-changed`, refuses any re-issuance under the old style, and notifies the voter.
2. The app prompts the voter to surrender the old token. When the device or phrase is gone, the old token is revoked through [escrow](#escrow).
3. Only after the old token is surrendered or revoked does the issuer accept a new device issuance under the new style key.
4. If the voter cast with the old token first, surrender fails, the cast ballot stands under the old style, and the jurisdiction's rules for a moved voter apply.

### Signing-key compromise

If a style's private key is compromised, anyone holding it can mint valid tokens for that style.

1. The authority signs a `DeviceKeyRevocation` with the operator key: the old key id, the new public key, and the instant of revocation. It is published to the ledger and in head notices. Because the definition is locked after activate, this signed record is the only way a style key changes, and it can only add a key for a style that already has one.
2. From that instant the casting endpoint refuses the old key, and the issuer signs only with the new one.
3. Voters with an unspent old-key token are notified and re-request from the app: the app surrenders the old token and requests a new one under the new key. Old tokens can no longer be cast, so surrender cannot race a cast.
4. Device ballots carry the style key id that authorized them (`deviceKeyId` in the payload), so validators can count ballots per key. That reveals only whether a ballot was cast before or after the revocation, which the hour or day timestamp already shows.

Tokens already cast under the compromised key cannot be told apart individually: a forged token's signature is as valid as an honest one, and no ballot is linked to a voter. Proposed: they stay counted. Validators compare, per compromised key, the cast count with the issued count minus surrenders and revocations. If cast does not exceed that, there is no evidence of forgery. If it does, the excess is a lower bound on forged ballots: the authority publishes it, and when it is at least the margin of any race on that style, the result for that race goes to the jurisdiction's recount or contest process. The individual ballots cannot be removed. This is open; see [Open questions](#open-questions).

### Threat notes

- Treat the phrase as a bearer secret, like the token itself. It derives `m` and `r`; turning them into a usable `s` also needs the blind signature, which lives only on the voter's phone and with the issuer, behind the voter's passkey. So the phrase alone is not quite a ballot, but the phrase plus the old phone, or plus a passkey enrolled by someone impersonating the voter at the clerk, is. Whoever casts first wins, and the voter cannot undo it. That is the same loss as a stolen mailed ballot, and the voter learns of it when their own cast or surrender is refused.
- The phrase alone also lets its holder compute that voter's token ids in every election of the tenant and, after close, see in the public spent list whether and how each token was used (cast, surrendered, or revoked). It never shows a ballot.
- One phrase covers every election of the tenant until it is rotated, so a theft covers them too. Rotation after exposure limits that to elections where the token was already cast.
- An assisted recovery keeps the phrase on the voter's own phone. Only an `assisted-shared` recovery puts it on another device, which never stores it and is opt-in per tenant; see [Assisted recovery](#assisted-recovery).
- The server stores `blinded`, the blind signature, and the escrow ciphertext. None of them link the voter to `tokenId` without `r` or k officials.
- 128 bits of seed is beyond brute force; the 12-word checksum catches typos, not attackers.

## Voting in person after a device issuance

A voter with a device issuance who turns up in person must not end up with two counted ballots. There are two paths.

### Surrender

If the voter still has the app or the phrase, they surrender the token before voting in person, from the app ahead of time or at the poll worker's device. Rotation, style changes, and key compromise use the same surrender.

1. The voter presents `m`, `r`, and `s` (the app derives them; at the polls the app shows them as a QR for the pollbook).
2. The app's passkey session, or the pollbook signed in as the official handling this voter, sends them to the issuer. The issuer checks that `Blind(K, m, r)` equals the `blinded` on this voter's device issuance, so the token is this voter's and nobody else's, and that `s` verifies.
3. In one transaction it inserts `SpentToken(tokenId, reason = surrendered)`. The unique constraint makes surrender and a concurrent cast race safely: one of them wins. If the token was already spent, surrender fails and, at the polls, the voter is offered a provisional ballot.
4. On success the device issuance is marked `voided` with reason "token surrendered". At the polls the voter is accepted on the pollbook as usual; in the app the voter may request a new token when the reason allows it.

Surrender reveals `tokenId` next to the voter's identity. That is safe: it shows only that this token was never used, which says nothing about how anyone voted. The token is burned, so it can never be linked to a ballot later.

### Provisional ballot

If the phrase and the device are both gone, the voter casts the jurisdiction's provisional ballot, and officials decide it through escrow.

#### Escrow

Decided: adopted, with a k-of-n threshold of election officials.

- **Who.** The n trustees are tenant officials named for the election. The authority sets `deviceEscrowThreshold` (k) and `deviceEscrowTrustees` (n) per election ([Settings](#settings)). k is at least 2 and n is at least k; activation is refused otherwise. The default is 2-of-3.
- **Key.** A new threshold ElGamal key for each election, made by a distributed key generation among the n officials before activation, so no official ever holds the whole private key. Its public key and the trustee roster are in the signed definition. After certification every official destroys their share and signs a destruction statement, which is published with the close bundle.
- **At issuance** the app encrypts `(m, r)` for this election to the escrow key and sends the ciphertext with `blinded`. The server stores it on the device issuance. Escrowing `(m, r)` rather than `tokenId` is what makes it checkable: an app that escrowed a made-up id could cast its real token and then claim a lost one. The seed is never escrowed, because it would expose the voter's other elections.
- **Opening.** k of the n officials jointly decrypt that one voter's ciphertext. They check `Blind(K, m, r)` equals the stored `blinded`. A mismatch means the app escrowed garbage; the provisional ballot is rejected.
- They look up `tokenId = SHA-256(m)` in the spent list:
  - not spent: insert `SpentToken(tokenId, reason = revoked)` and count the provisional ballot (or, for a style change or a rotation, allow a new issuance);
  - spent (cast or surrendered): reject the provisional ballot.
- **Publication.** Every opening is an `EscrowOpening` (election, `tokenId`, purpose, outcome, the k officials' signatures), and every revocation is its `SpentToken` row. Both are appended to the public ledger when they happen and appear in head notices, without the voter's name. An opening without a matching provisional ballot, style change, or rotation shows up in the reconciliation.

**Requirement.** Escrow is safe only if the spent list can never be joined to a ballot. If it could, opening a voter's escrow would reveal their vote. So:

- `SpentToken` and the ballot tables share no key and no reference: no `ballotId`, no ballot hash, no token data on the ballot beyond the style key id that every ballot of that style and period shares.
- `SpentToken` carries no timestamp, not even a truncated one, and its rows are published sorted by `tokenId`.
- The cast writes the spent row and hands the ballot to a separate pending store in a separate step (below), so no transaction id or commit order joins them in the database or its write-ahead log.
- Device ballots reach the chain only in shuffled batches, after the random delay, with day-rounded timestamps by default.

Tradeoffs:

- Officials can decrypt more than they should. That is why it is k-of-n with k at least 2, a fresh distributed key per election destroyed after certification, every opening published, and one ciphertext per voter per election.
- The host runs the casting endpoint. A host that instruments it live sees which token arrived with which ballot. Without escrow that link reveals nothing about the voter, because the token is blind. With escrow, a malicious host plus one opening reveals that voter's vote, and a malicious host plus k colluding officials could reveal every device voter's vote. The separation above limits what is *stored*; it does not stop a live attacker who controls both.

The alternative without escrow is in footnote [^no-escrow].

### Closing order

Device casting closes at `votingEndDate`, with issuance. Provisional adjudication happens after that. A revocation commits a `SpentToken` row, so a revoked token can never be cast afterwards in any case; closing first also means adjudication sees the final spent list.

A token is spent when the casting endpoint accepts the cast, which is when the `SpentToken(reason = cast)` row commits. It is not spent when the app schedules the cast, and not when the ballot is later appended to the chain. A cast still waiting out its random delay in the app is not spent. The app sends before `deviceCastCutoffMinutes` ahead of polls close, and the casting endpoint refuses anything after `votingEndDate`, so a delayed cast either lands in time or never counts, and the voter sees which.

## Issuance window

Decided: issuance opens a configurable number of days before voting starts and closes when polls close.

- `Election.deviceTokenLeadDays` (Integer, default 14, 0 to 60) is the election's value; device voting itself is `Election.deviceVoting`. The window opens at `votingStartDate` minus that many days and closes at `votingEndDate`, the existing polls-close instant.
- An override can be set per district or per precinct. The most specific one wins: precinct, then district, then election.
  - Precinct: `ElectionVotingPrecinctAssignment.deviceTokenLeadDays`, on the row that already ties a precinct to the election.
  - District: a new `ElectionDistrictSetting` row (`election`, `votingDistrict`, `deviceTokenLeadDays`), unique on (`election`, `votingDistrict`). A precinct reaches its districts through `PrecinctDistrict`.
  - A precinct in two districts with different overrides and no precinct override is ambiguous. The service refuses to activate until a precinct override settles it.
- The resolved opening for each precinct goes into the signed election definition, so validators and voters see the same window the issuer enforces. Changing it after activate is not possible, like every other part of the definition.
- Issuance needs the election `active`, because the style keys are only published by the signed definition. Officials therefore activate before the earliest opening. Ballots are still refused before `votingStartDate`: a token fetched early is held in the app until voting starts, and the random delay below runs from the later of issuance and voting start.
- Casting closes at `votingEndDate` too. A token that is not spent by then is never spent.

Opening earlier widens the anonymity set for timing, because more tokens are outstanding when the first device ballots arrive.

## Settings

Decided: every timing or size choice is a setting with a default. Election-level settings are fields on `Election` and go into the signed definition, so the issuer, the casting endpoint, the import workstation, the app, and validators all read the same values, and none change after activate. A tenant can keep its own defaults for new elections in a `TenantDeviceSetting` row with the same field names. Tenant-level operational settings are fields on `Tenant`. Names follow the existing camelCase entity fields; the columns are their snake_case form.

| Setting | Level | Default | Allowed | Meaning |
| --- | --- | --- | --- | --- |
| `deviceVoting` | election | `false` | boolean | Device voting is offered for this election. |
| `deviceTokenLeadDays` | election, district, precinct | 14 | 0 to 60 | Issuance opens this many days before `votingStartDate`; the most specific level wins ([Issuance window](#issuance-window)). |
| `deviceCastDelayMinMinutes` | election | 10 | 0 to 240 | Shortest random delay before the app sends a cast. |
| `deviceCastDelayMaxMinutes` | election | 60 | min to 1440 | Longest random delay. |
| `deviceCastCutoffMinutes` | election | 30 | 5 to 240 | The app sends no later than this long before polls close. Inside the last `cutoff + max` minutes the app shrinks the delay range to fit automatically. |
| `deviceCastDelayVoterShortenable` | election | `false` | boolean | Whether the voter may choose "send now" inside that final period. Off by default: the automatic shrink already guarantees the ballot lands in time, and a voter-chosen instant send is a timing marker. |
| `deviceBallotTimestampPrecision` | election | `day` | `hour`, `day` | How the casting endpoint rounds a device ballot's `timestamp`. |
| `deviceSharedRecoveryAllowed` | tenant | `false` | boolean | Whether a voter with no phone of their own may recover on a shared or official device (`assisted-shared`). |
| `deviceReissueLimitPerDay` | tenant | 3 | 1 to 10 | Voter-initiated re-issuances per voter per UTC day. |
| `deviceStyleMinimumVoters` | election | 100 | 10 to 10000 | Eligible voters a style needs before it counts as not small. |
| `deviceSmallStyleAction` | election | `off` | `off`, `merge`, `flag` | What happens to a small style ([Small styles](#small-styles)). |
| `ElectionVotingPrecinctAssignment.deviceMergeWithPrecinctId` | precinct | empty | a precinct of the same election with the same races | The merge target when the action is `merge`. A small style with no valid target falls back to `off`. |
| `deviceImportIntervalMinutes` | election | 60 | 15 to 1440 | How often the import workstation appends pending device ballots during voting. |
| `deviceImportMinimumBatch` | election | 25 | 10 to 1000 | Smallest batch it appends; below it, it waits for the next interval. At polls close it appends whatever remains. 25 matches the received-mark batch default (`SUREVOTE_RECEIVED_BATCH_SIZE`). |
| `deviceRelayMode` | election | `best-effort` | `best-effort`, `required` | `best-effort` uses the Oblivious HTTP relay when it answers and falls back to a direct connection; `required` refuses to send without it. |
| `deviceTorAllowed` | election | `true` | boolean | The app offers Tor as an alternative transport the voter can choose. |
| `deviceEscrowThreshold` | election | 2 | 2 to n | k, the officials needed to open an escrow. Activation is refused below 2. |
| `deviceEscrowTrustees` | election | 3 | k to 15 | n, the officials holding escrow key shares. |

Not settings, by decision:

- Issuance and casting close at `votingEndDate`.
- A precinct whose districts carry different `deviceTokenLeadDays` overrides and that has no precinct override is ambiguous. Activation is refused until a precinct override settles it.
- The voter is always notified of a re-issuance or rotation.

## Casting

1. The voter fills in the ballot in the app from the signed definition, which the app fetched and verified against the operator key. The app enforces the style and the rules in [What may be on a ballot](./system.md#what-may-be-on-a-ballot).
2. The app waits a random delay before sending. It schedules the cast for a uniformly random time between `deviceCastDelayMinMinutes` and `deviceCastDelayMaxMinutes` (default 10 to 60) after the later of token issuance and `votingStartDate`, and never later than `deviceCastCutoffMinutes` before polls close; near the end the range shrinks to fit. If the voter casts after the maximum delay has already passed since issuance, the app sends at once. The voter sees "your ballot will be sent at about 14:20" and can leave the app; the cast runs as a background task, and runs at next open if the OS killed it.
3. The app sends `POST https://cast.<host>/device-ballots` with the ballot payload, the vote payloads, `m`, `s`, and `styleKeyId`. The request uses a separate HTTP client: no cookie jar, no `Authorization`, no custom headers beyond `Content-Type`, a generic user agent, and no TLS session resumption shared with the issuer host. It goes through an Oblivious HTTP relay (RFC 9458) run by a party other than the host, so the casting host sees the relay's address, not the voter's. With `deviceRelayMode` `best-effort` (the default) the app falls back to a direct connection when the relay does not answer; with `required` it waits for the relay. When `deviceTorAllowed` is on, the voter can choose Tor instead.
4. The casting endpoint verifies `s` on `m` under the named style key, that the key belongs to this election and is not revoked, and that casting is open. It commits `SpentToken(electionId, keyId, tokenId, m, s, reason = cast)`, unique on `(electionId, tokenId)`. A second cast with the same token is refused with "token already spent".
5. Only after that commit, it hands the ballot and its votes to a pending store outside the ledger database, in the same way the received queue keeps issuance ids outside it. The pending ballot carries no token data. If the hand-off fails, the endpoint retries it; the token is already spent, so the ballot must not be dropped.
6. The response is the ballot hash and the endpoint's signature over it. The app then shows the access code once, exactly like the in-person slip, and wipes `m`, `r`, `s`, and the code from memory after the voter confirms they saved it. The seed stays, for later elections, until the voter removes it.

A lost response is not retried as a new cast: the retry gets "token already spent". The app instead looks the ballot up with its access code once it is on the chain. Pending ballots are searchable by `voterAccessHash` until imported, so the app can confirm sooner.

The authority's import workstation pulls pending device ballots every `deviceImportIntervalMinutes` (default 60), waits until at least `deviceImportMinimumBatch` (default 25) are pending except at polls close, checks each ballot against its style, shuffles the batch, and appends the chain entries with the import key, as it does for media from the polls. Batching keeps chain order from showing arrival order.

The ballot payload uses type `device`. `recordingDeviceId`, `recordingPlaceId`, and `recordedBy` are null. Its `timestamp` is set by the casting endpoint and rounded down to the day by default (`deviceBallotTimestampPrecision`, `hour` or `day`), so the ledger does not carry a precise arrival time. `deviceKeyId` names the style key that authorized it. There is no device signature and no other token data on the ballot.

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
- every `EscrowOpening` and every `DeviceKeyRevocation`, which were also appended to the ledger as they happened;
- the escrow officials' share-destruction statements.

A validator checks every `s` under its key, that no `tokenId` repeats, that per style key the `cast` rows equal the device ballots naming that `deviceKeyId`, and that cast plus surrendered plus revoked rows do not exceed issued tokens. Each revoked row must have a matching opening signed by at least k of the named officials. Cast rows under a revoked key are compared with that key's issued count as in [Signing-key compromise](#signing-key-compromise).

## Privacy limits

- **Timing.** The host sees issuance times and, rounded to the day (or hour, if so set), cast times. The random delay, the rounding, and a relay make a match a guess among everyone of that style who got a token earlier. Early in the window, or in a small style, that guess can be good.
- <a id="small-styles"></a>**Small styles.** The anonymity set is per style, and a style with very few voters can be close to identifying, with or without device voting. A style below `deviceStyleMinimumVoters` (default 100) is small, and `deviceSmallStyleAction` picks the mitigation, `off` by default:
  - `off`: device voting is off for that style; its voters use mail or in person;
  - `merge`: a tiny precinct, through `deviceMergeWithPrecinctId`, shares a style key and is reported with a neighboring precinct that has the same races, so its device ballots name the merged group, not the precinct;
  - `flag`: the definition marks the style as small, the issuer warns the voter that their ballot may be identifiable, and the canvass statement lists the flagged styles.
- **The app is a trusted client.** It sees the selections, the seed, and the code. Open source and reproducible builds help. A voter can check the result by looking the ballot up on another device with the code.
- **Coercion.** Same as in person: the access code is a bearer token, and someone standing over the voter sees the ballot. The phrase adds a second bearer secret a coercer can demand. This design adds no coercion resistance.
- **Escrow.** It adds the official-collusion and live-host risks in [Escrow](#escrow).

## Data model changes (proposed, not built)

- `ElectionDeviceKey`: `electionId`, `ballotStyleKey` (the precinct, or merged precinct group, and party it covers), `keyId`, `publicKey`, `small` (Boolean), listed in the signed definition.
- The [settings](#settings): fields on `Election`, `ElectionDistrictSetting` (`election`, `votingDistrict`, `deviceTokenLeadDays`), `ElectionVotingPrecinctAssignment` (`deviceTokenLeadDays`, `deviceMergeWithPrecinctId`), `Tenant.deviceReissueLimitPerDay`, `Tenant.deviceSharedRecoveryAllowed`, and the optional `TenantDeviceSetting` defaults.
- `Election.escrowPublicKey` and `ElectionEscrowTrustee` (`election`, `tenantOfficial`, share index, public share), in the signed definition; `EscrowShareDestruction` (trustee, signature), published at close.
- `DeviceKeyRevocation`: `electionId`, `revokedKeyId`, new `ElectionDeviceKey`, `revokedAt`, operator signature. Public, insert-only, appended to the ledger.
- `BallotIssuance.channel`: `in-person`, `mail`, `device`. A device row also has the style key id, `blindedMessage`, `blindSignature`, and `escrowCiphertext`, and no recording place.
- `BallotIssuance.status` gains `style-changed`.
- `BallotIssuanceEvent`: private, per issuance: `reissued`, `phrase-rotated`, `surrendered` (each of these three with an `assisted` flag: empty, `assisted`, or `assisted-shared`), `style-changed`, `key-revoked`, `escrow-opened`, `notified`, with a time.
- `SpentToken`: `electionId`, `keyId`, `tokenId`, `message`, `signature`, `reason` (`cast`, `surrendered`, `revoked`). Insert-only, unique on `(electionId, tokenId)`. No ballot reference and no timestamp columns, so the usual ledger audit fields are left off this table on purpose. Revoked rows are appended to the ledger when they commit; the full list is published at close.
- `EscrowOpening`: `electionId`, `tokenId`, `purpose` (`provisional`, `style-change`, `rotation`), `outcome` (`revoked`, `spent`, `mismatch`), the k officials' signatures. Public, insert-only, appended to the ledger.
- A pending device-ballot store outside the ledger database.
- `BallotType.DEVICE`, and `deviceKeyId` on the device ballot payload.
- Reconciliation gains a device row per style: issued tokens, cast, surrendered, revoked, and device ballots, matched when cast equals device ballots and the sum does not exceed issued.

## Open questions

Decided and moved into the sections above: the issuance window, one key per style, secure storage with one recovery phrase per voter per tenant, rotation, voter-initiated byte-identical re-issuance, surrender, escrow with k-of-n officials, the closing order, and every configurable value in [Settings](#settings). Ambiguous district overrides keep refusing activation.

These are not settings, so they stay open. Each has a recommendation that is pending Josh's OK and is not decided:

1. **Enrollment.** Mailed code, in person, or both? Recommended, pending Josh's OK: support both, with the mailed code as the default; identity proofing beyond the roll stays the authority's call.
2. **Coercion and re-voting.** Recommended, pending Josh's OK: accept bearer semantics for version one, as for the in-person access code, and revisit a recast-and-last-counts scheme only if device voting is adopted beyond pilots. Re-voting conflicts with the one-token rule and with escrow.
3. **HSM.** Recommended, pending Josh's OK: style keys in an HSM in the authority's custody, such as a YubiHSM 2 attached to an authority-run signer that the issuer calls, or CloudHSM in an account the authority owns. Not in the host's account: the host already runs the casting endpoint.
4. **Ballots already cast under a compromised key.** Recommended, pending Josh's OK: the proposal in [Signing-key compromise](#signing-key-compromise): count them, publish per-key cast against issued, and refer an excess that reaches a race's margin to the jurisdiction's recount or contest process.
5. **Escrow key ceremony.** Recommended, pending Josh's OK: Pedersen distributed key generation and threshold ElGamal over ristretto255, with each official's share on a hardware token, run on the authority workstations before activation.

## Not in this design

The app itself, the issuer and casting services, the token code, and the schema changes above. They are not started. No scaffold was added. The decided parts fix the token, key, and window shapes, but the enrollment and HSM questions still change the services' API and deployment.

[^no-escrow]: Without escrow, the jurisdiction's existing provisional-ballot rules would decide, using the device issuance, the voter's statement, and the style's issued and spent counts at close. If every token in the style was spent, the provisional is a likely second ballot; otherwise it is not decidable for one voter. No vote could ever be revealed, at the cost of either counting some double votes or rejecting some honest provisionals, and style changes and rotations after a lost device would have no revoke path. Not adopted.
