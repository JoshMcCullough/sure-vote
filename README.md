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
- `repository` — storage access. Hides the db. Tests use an in-memory store. PostgreSQL implements the same contract through JDBC and Flyway.
- `service` — recording, chain append, tallies, and cross-entity rules.
- `resource` — projections returned to clients.
- `api` — HTTP. Calls the service and returns resources.
- `style-checks` — Checkstyle rules from [style.md](./docs/style.md).
- `ui` — React workspace and public lookup, served at `/app`.

## Code

`./gradlew check` compiles the modules, runs Checkstyle, and runs the tests. The HTTP API is the JDK server with Jackson for JSON. Lombok remains only on the administrative `Resource` base. Response types do not extend it.

The in-person path is implemented: seal a ballot, import it, append one chain entry per vote, and look the ballot up with the access code. The plaintext code is not stored. `ballotHash` inside a vote is the SHA-256 of that ballot's payload with `voteHashes` set to `[]`. It is not `Ballot.hash`. See [verification.md](./docs/verification.md).

Also implemented: issuance during an active election, void before a machine session, showcase rejection of `ssn` and `voterId`, and first-past-the-post, referendum, and ranked-choice tallies when the election closes. A write-in name is counted on its own. A tie stays tied. Ranked choice elects one winner by instant runoff. `votesReceived` on that race is the first-choice count. Close publishes one operator-signed canvass statement.

Mail-in and absentee recording run on the authority workstation. The viewing passphrase is stored only as a box sealed to the election viewing key. `voterAccessHash` is Argon2id of that passphrase with the raw access code as the salt. The ballot and the accepted issuance commit only after delivery of the code is accepted. A refused handoff rolls both back. The host import checks the sealed ballot and does not receive the code or the passphrase. `POST /elections/{id}/lookup` takes the code and the passphrase in the body. `GET /elections/{id}/ballots?code=` stays the in-person lookup.

A voter files a `BallotDispute` with the same code. The claim is private. The voter can read it back with that code. `GET /elections/{id}/disputes/count` publishes the count and not the statement. An official with `X-Actor-Id` may mark a claim `noted` or `dismissed`. None of those steps change the ballot, the chain, or the tally.

A production election cannot become active until the tenant has registered an outside validator. The host stores that public key and not the private key. `GET /elections/{id}/heads` publishes each race head once the election is active. A validator files a signed result for a real chain prefix. The result does not change the tally. `witnessGap` is true when an active chain has grown and no registered validator has signed the current head within 24 hours. Recording does not stop. A showcase election may activate without a validator.

After a divergent restore, an official files the operator-signed `RestoreIncident`. The service builds the payload from the current chain heads and the latest registered-validator witnesses, refuses it when every witness is still a prefix of the restored chain, and publishes it at `GET /elections/{id}/restore-incidents`. It does not change the ledger or the tally.

The React app in `ui/` is the public lookup and the election workspace. On a showcase tenant it configures an election, casts one demonstration ballot, shows the access code once, and closes. On a production tenant the same configuration screens apply, and activate and close still take a signature made outside the app. The browser does not hold a production private key. See [ui.md](./docs/ui.md).

PostgreSQL stores the same rows. Migration `V3__ledger_guards.sql` adds the immutability triggers from the PostgreSQL strategy: ballots, votes, chain entries, canvass statements, restore incidents, and held keys reject `UPDATE` and `DELETE`. A chain header may change only its tip and status, may move only from `active` to `closed`, and needs a hash and a signature once closed. A validation result keeps its signed fields and cannot be deleted. No row may change its `tenant_id`. `V4__references.sql` adds foreign keys for every reference column and for `tenant_id`, and rejects a child whose `tenant_id` differs from its parent's. It also repeats four service rules: a vote's ballot and race candidate are in the same election, a ballot's precinct has a current assignment to its election, a chain entry is on the chain of its vote's race, and a production election cannot become `active` without a registered outside validator. `./gradlew check` also runs every service test against PostgreSQL through the `postgresTest` task. Row-level security and the role split are not in this slice. A new recording device is rejected once any election for that tenant has left `upcoming`, because the signed definition lists every active device.

## Run

`./gradlew :api:run` starts the API on port 8080 with an in-memory store. `SUREVOTE_JDBC_URL`, `SUREVOTE_JDBC_USER`, and `SUREVOTE_JDBC_PASSWORD` select PostgreSQL and run migrations first. `SUREVOTE_PORT` changes the port. When `ui/dist/index.html` exists, the API serves the built app at `/app`. `SUREVOTE_STATIC` overrides that directory.

From `ui/`, `npm install` then `npm run dev` starts the dev server and proxies the API at `http://127.0.0.1:8080`. `npm run build` writes `ui/dist`.

## Documents

- [How the system works](./docs/system.md)
- [Verifying a vote chain](./docs/verification.md)
- [License](./LICENSE)
- [Schema](./docs/schema.md)
- [Constraints](./docs/constraints.md)
- [PostgreSQL strategy](./docs/postgres-strategy.md)
- [Code style](./docs/style.md)
- [Election app](./docs/ui.md)
