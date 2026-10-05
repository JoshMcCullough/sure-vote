# SureVote

SureVote is an election authority's system of record and a public ballot ledger. Officials keep the voter roll and record in-person and mail ballots. Each recorded vote is appended to a hash chain for its race. A voter can open their ballot with an access code only they hold. Anyone can recompute the chain and check a published result against it.

The chain lives in PostgreSQL. A copy of the chain head held outside that db is what makes a later rewrite detectable. [system.md](./docs/system.md) is the operating model: who does what, what is public, how a ballot is recorded, and which decisions are still open.

## Goals

- The repository is public under a source-available license. Anyone may read it and run it in private to study it. Conducting an election with it requires a separate agreement with Sure Vote Systems, the copyright holder. See [LICENSE](./LICENSE).
- Sure Vote Systems runs a free public website where anyone can create a virtual election and walk through recording, receipts, lookup, and chain verification. Virtual elections are demonstrations. They have no legal effect and do not use real voter rolls.
- A lookup page on every deployment lets a voter scan a QR code or type a text code to open the ballot that code names. A mail-in lookup also requires the passphrase the voter set at registration. Checking a published chain does not require this codebase. Published instructions, the public API, an SDK, or an independent program are enough. See [verification.md](./docs/verification.md).
- When a ballot is recorded, the voter receives that code as a QR code and as text. In person, an offline voting machine prints it at commit and the voter takes it. The machine never learns the voter id. For mail-in, the code is generated when the returned ballot is recorded and sent to the email, phone, or registration address the voter checked on the envelope. It is not printed on the outgoing ballot, and it is not written on the voter row. The letter alone cannot open the ballot.
- A ballot on the ledger is final. Before commit, an in-person voter can correct the selections. After commit, the voter can file a dispute. The dispute does not change the ballot or the tally.
- Sure Vote Systems hosts the service for many election authorities on one deployment. A small locality is a tenant on that service, not its own installation. An authority that runs its own instance does so under a separate agreement. Tenancy, residency, backups, and outside copies of chain heads are in [system.md](./docs/system.md).

## Capabilities

- Publish an election definition: races, candidates, precincts, hash algorithm, and payload schemas.
- Record a finished ballot and append one chain entry per vote.
- Give the voter an access code as a QR code and as text. The ledger stores a hash of that code and does not store a voter id on the ballot.
- Let a voter open an in-person ballot with the access code. A mail-in ballot also requires the registration passphrase. The same lookup is available on a terminal at an election office. The voter enters the code. The official does not see it.
- Let any verifier recompute the chain. Named validators can file a signed result.
- Recompute candidate tallies from the chain.
- Publish one signed canvass statement when the election closes. Precinct, district, and locality totals in it are sums of the race chains.

## Not in this design

- A consensus network or a second operator.
- A voter-controlled device submitting a binding ballot.
- Coercion resistance. An in-person access code is a bearer token. Anyone with that slip can open that ballot.
- A networked voting machine. Polling-place machines stay offline and write ballots to local media.
- A ledger-level proof that every ballot came from an eligible voter exactly once. That check is private and is still an open decision in [system.md](./docs/system.md).
- A parent chain per precinct, district, state, or nation. Geographic totals are the canvass statement.
- Provisional ballots. A voter who is not accepted is not recorded.

## Modules

- `entity` — domain records used by business logic and storage.
- `repository` — storage access. Hides the db.
- `service` — recording, chain append, tallies, and cross-entity rules.
- `resource` — projections returned to clients.
- `api` — HTTP. Calls the service and returns resources.

## Documents

- [How the system works](./docs/system.md)
- [Verifying a vote chain](./docs/verification.md)
- [License](./LICENSE)
- [Schema](./docs/schema.md)
- [Constraints](./docs/constraints.md)
- [PostgreSQL strategy](./docs/postgres-strategy.md)
- [Code style](./docs/style.md)
