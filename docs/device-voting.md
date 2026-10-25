# Voting from the voter's device

Status: design only. Nothing here is built. This is decision 3 in [system.md](./system.md#open-decisions): a voter casts a binding ballot from their own phone through a React Native app. The mail and in-person paths do not change and keep their `BallotIssuance` rows.

Decided: device voting **requires** a blind-signed one-time token. The voter signs in only to get that token. The ballot is cast later, separately, with the token and nothing that names the voter or the device. Without the token there is no device channel.

## Goals

- One device ballot per eligible voter, enforced by the issuer, with no record anywhere of which ballot is whose.
- A cast request that carries no session, cookie, account, device id, push token, or advertising id. The only proof is the token.
- Anyone can check from the close bundle that every device ballot spent a distinct valid token, and that no style has more device ballots than tokens issued.
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

- **Token issuing key.** RSA-3072, one per election and ballot style, used with RSA blind signatures, `RSABSSA-SHA384-PSS-Randomized` from RFC 9474. One key per style lets the token prove which ballot the voter may cast without carrying a voter id; the anonymity set is everyone with that style who got a token. The public keys are listed in the election definition, so the operator signature freezes them before activate. The private key signs on demand during the window, so it sits in an HSM that can do raw RSA on a blinded message, such as AWS CloudHSM or a YubiHSM 2. AWS KMS signs only whole messages and cannot do this. The key is destroyed after close.
- **Voter passkey.** Ordinary WebAuthn, used only at the issuer.
- **Token secret.** 32 random bytes generated in the app, never sent to the issuer.
- **Access code.** 32 random bytes generated in the app at cast, with `voterAccessHash` computed in the app as for in person (SHA-256 over the raw bytes; see [verification.md](./verification.md)). The code is never sent.
- No device key. A per-device signing key would be a device id on every ballot.

## Getting a token

1. The app signs in at `https://issue.<host>` with the passkey.
2. The issuer checks the voter is eligible, the election is `active` or in its token window, and the voter has no accepted `BallotIssuance` for the election. It answers with the voter's ballot style key id. A voter with no party in a primary picks a slate here, which fixes the style.
3. The app draws the token secret `t` and builds the message `m = "SureVote-device-token-v1" || electionId || styleKeyId || t`. It blinds `m` under the style's public key and sends only the blinded message.
4. In one transaction the issuer writes a `BallotIssuance` with channel `device` and status `issued`, then signs the blinded message. If the transaction fails, nothing is signed. A second request for the same voter is refused, like a second in-person acceptance.
5. The app unblinds the signature to `s`, checks `s` verifies on `m`, and keeps `(t, s)` in the platform keystore. The sign-in session ends. The app drops its cookies for the issuer host.

The device `BallotIssuance` is never marked `received`. The issuer does not know when, or whether, the token is spent, and must not. It is the issuer's spend of that voter's eligibility. Mail and in-person paths see an accepted row and refuse a second ballot.

## Casting

1. The voter fills in the ballot in the app from the signed definition, which the app fetched and verified against the operator key. The app enforces the style and the rules in [What may be on a ballot](./system.md#what-may-be-on-a-ballot).
2. The app waits a random delay before sending. It schedules the cast for a uniformly random time between 10 and 60 minutes after token issuance, but never later than a safety margin before the window ends. If the voter casts more than an hour after issuance, the delay already exists and the app sends at once. The voter sees "your ballot will be sent at about 14:20" and can leave the app; the cast runs as a background task, and runs at next open if the OS killed it.
3. The app sends `POST https://cast.<host>/device-ballots` with the ballot payload, the vote payloads, `t`, `s`, and `styleKeyId`. The request uses a separate HTTP client: no cookie jar, no `Authorization`, no custom headers beyond `Content-Type`, a generic user agent, and no TLS session resumption shared with the issuer host. It goes through an Oblivious HTTP relay (RFC 9458) run by a party other than the host when one is available, so the casting host sees the relay's address, not the voter's.
4. The casting endpoint, in one transaction:
   - verifies `s` on `m` under the named style key, and that the key belongs to this election and is still in its window;
   - inserts `SpentToken(electionId, styleKeyId, tokenHash = SHA-256(t))`, unique on `(electionId, tokenHash)`;
   - stores the ballot and its votes as pending. It does not append to the chain, because the host does not hold the import key.
   A second cast with the same `t` and the same ballot hash returns the first result, so a retry after a lost response is safe. The same `t` with a different ballot is refused with "token already spent".
5. The response is the ballot hash and the endpoint's signature over it. The app then shows the access code once, exactly like the in-person slip, and wipes `t`, `s`, and the code from memory and the keystore after the voter confirms they saved it.

The authority's import workstation pulls pending device ballots in batches, checks each token proof, shuffles the batch, and appends the chain entries with the import key, as it does for media from the polls. Batching keeps chain order from showing arrival order. A device ballot is accepted, and its token spent, at step 4; the append only places it on the chain.

The ballot payload uses type `device`. `recordingDeviceId`, `recordingPlaceId`, and `recordedBy` are null. Its `timestamp` is set by the casting endpoint and truncated to the hour, so the ledger does not carry a millisecond arrival time. The token proof `(styleKeyId, t, s)` is published with the ballot in the close bundle, and it is not in the ballot hash. There is no device signature on the ballot. The token proof is what authorizes it.

The issuer never calls the casting host and the casting host never calls the issuer. They share the election definition and nothing else.

## Separate endpoints

- Different hostnames, certificates, load balancers, and WAF web ACLs. CloudFront logging stays off on both, as in `infra/`.
- The casting host keeps no IP or request log. It cannot rate-limit by IP for the same reason, and the relay would defeat that anyway. Its rate limit is the token: a request without a valid signature is rejected before any write, and signature checks are cheap enough to absorb abuse at the edge.
- The issuer logs sign-ins as it does for officials. Those logs never sit next to casting data.
- Separate database roles. The casting role can insert ballots, votes, entries, and `SpentToken`, and cannot read `person`, `voter_registration`, `credential`, or `ballot_issuance`. The issuer role cannot read `ballot` or `SpentToken`. Both are still the host. This separation is what an honest host keeps; it is not a proof against a dishonest one.

## What validators can check

At close the bundle adds:

- each style key and its issued count, taken from the device `BallotIssuance` rows grouped by style;
- every `SpentToken` with its device ballot's hash and the token proof.

A validator checks every proof under the right key, that no `tokenHash` repeats, and that device ballots per style do not exceed issued tokens. Issued minus spent is the number of tokens that were never used.

## Privacy limits

- **Timing.** The host sees issuance times and, truncated to the hour, cast times. The random delay, the hour truncation, and a relay make a match a guess among everyone of that style who got a token earlier. Early in the window, or in a small style, that guess can be good.
- **Small styles.** The anonymity set is per style. A style with very few device voters can be close to identifying. Below a threshold the authority should not offer device voting for that style.
- **The app is a trusted client.** It sees the selections and the code. Open source and reproducible builds help. A voter can check the result by looking the ballot up on another device with the code.
- **Coercion.** Same as in person: the access code is a bearer token, and someone standing over the voter sees the ballot. This design adds no coercion resistance.
- **Lost token.** If the phone dies after issuance and before cast, the token is gone with it. The issuer cannot tell whether it was spent, so it cannot safely issue another.

## Data model changes (proposed, not built)

- `ElectionDeviceKey`: `electionId`, `ballotStyleKey` (the precinct and party it covers), `keyId`, `publicKey`, listed in the signed definition.
- `SpentToken`: `electionId`, `keyId`, `tokenHash`, `tokenSignature`, `ballotId`. Insert-only, unique on `(electionId, tokenHash)`, published at close.
- `BallotIssuance.channel`: `in-person`, `mail`, `device`. A device row has the style key id and no recording place.
- `BallotType.DEVICE`.
- Reconciliation gains a device row per style: issued tokens and device ballots, matched when ballots do not exceed issued.

## Open questions for Josh

1. **Lost token after issuance.** Strand the voter, or allow a provisional in-person ballot that is counted only if, at close, the voter's style has fewer spent tokens than issued? The second leaks a little and needs officials to adjudicate.
2. **Token window.** Issue only while the election is `active`, or open issuance earlier so tokens can be fetched days before casting, which widens the anonymity set a lot?
3. **Key granularity.** One key per ballot style, or partially blind RSA with public metadata (`draft-irtf-cfrg-partially-blind-rsa`), which uses one key per election but is not yet an RFC?
4. **Relay.** Require an Oblivious HTTP relay run by an outside party, make it best-effort, or offer Tor as an option?
5. **Minimum anonymity set.** What threshold of eligible device voters per style turns device voting off for that style?
6. **Delay bounds.** Is 10 to 60 minutes the right default, and should the voter be able to shorten it near the end of the window?
7. **Timestamp truncation.** Hour, or day? Day loses the ordering within a day that a validator might want for a witness gap.
8. **Enrollment.** Mailed code only, in person only, or both? Identity proofing beyond the roll is the authority's call; the system needs to know which it supports.
9. **Coercion and re-voting.** Accept bearer-code semantics for version one, or plan for a later scheme where a voter can recast and only the last counts?
10. **Import cadence.** How often the import workstation appends device batches during the window, and what minimum batch size it waits for. More often means fresher heads for validators; bigger batches hide arrival order better.
11. **HSM.** CloudHSM in the host account, or a YubiHSM 2 in the authority's custody connected to the issuer? The second fits the custody rules better but is harder to run online for a whole window.

## Not in this design

The app itself, the issuer and casting services, the token code, and the schema changes above. They are not started. No scaffold was added, because questions 1, 2, and 3 change the API and the schema.
