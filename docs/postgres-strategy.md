# PostgreSQL Strategy

Storage plan for the operating model in [system.md](./system.md), the fields in [schema.md](./schema.md), and the rules in [constraints.md](./constraints.md).

## 1) Core Decisions

- Use **PostgreSQL** as the db of record.
- Use **UUID PKs** (`gen_random_uuid()`), `timestamptz` for all date/time fields, `date` where the field is a calendar date.
- `ballot`, `vote`, and `vote_chain_entry` are insert-only.
- `vote_chain` is a mutable header. Updates may change only `latest_entry_id`, `length`, `hash`, `signature`, `status`, `updated_at`, `updated_by`, and `version`.
- Store large third-party validation artifacts in object storage. Keep references in the db.
- The public ledger is tamper-evident after a chain head has been copied outside this db. A validator's signed `vote_chain_validation_result` is that copy.

## 2) Table Layout by Domain

### Geography / Identity (mostly mutable)

- `tenant`
- `tenant_validator`
- `locality`
- `address`
- `person`
- `candidate`
- `elected_official`
- `voter_registration`
- `ballot_issuance` (private; at most one `accepted` row per person and election; voided rows remain; no ballot id and no access code)
- `ballot_dispute` (private; references `ballot`; does not modify the ledger)

### Elections / Configuration (mutable before election, locked later)

- `voting_district`
- `voting_precinct`
- `precinct_district`
- `ballot_style`
- `election`
- `race`
- `race_candidate`
- `election_voting_precinct_assignment`
- `recording_place`
- `recording_device`
- `seat`
- `term`

### Voting ledger

`ballot`, `vote`, and `vote_chain_entry` are append-only. `vote_chain` is the mutable header described above.

- `ballot`
- `vote`
- `vote_chain`
- `vote_chain_entry`
- `vote_chain_validator`
- `vote_chain_validation_result`
- `canvass_statement` (one per election, insert-only, written at close)
- `restore_incident` (one per election, insert-only, only after a divergent restore)

Every table except `vote_chain_validator` has `tenant_id`. The column is immutable after insert. `vote_chain_validator` is global.

## 3) Suggested SQL Types

- IDs: `uuid`
- Date-only semantics: `date`
- Timestamps: `timestamptz`
- Hash/signature values: `text` (or `bytea` if raw bytes)
- Enum-like values: `text` + `CHECK (...)` initially (move to PG enums later if stable)

## 4) Indexing Plan

### High-value indexes

- `race(election_id, round)`
- `race_candidate(race_id, candidate_id)` unique
- `ballot(election_id, timestamp)`
- `vote(ballot_id)`
- `vote(race_candidate_id)`
- `ballot(election_id, voter_access_hash)` unique
- `ballot_issuance(person_id, election_id)` unique where `status = 'accepted'`
- `vote_chain(race_id)` unique where `status = 'active'`
- `vote_chain_entry(previous_entry_id)`
- `vote_chain_entry(vote_id)` unique
- `vote_chain_entry(vote_chain_id, index)` unique
- `vote_chain_validation_result(vote_chain_id, date_validated desc)`
- `canvass_statement(election_id)` unique
- `restore_incident(election_id)` unique
- `tenant_validator(tenant_id, validator_id)` unique
- `tenant_id` on every tenant-scoped table

### Optional scale indexes

- BRIN index on `ballot(timestamp)` and `vote_chain_entry(timestamp)` for large append-only volumes.

## 5) Partitioning Plan (when volume grows)

Start without partitioning; add once tables are large enough to justify operational overhead.

- Partition `ballot` by `election_id` (LIST) or by `timestamp` (RANGE, monthly).
- Partition `vote` by `ballot.election_id` denormalized onto `vote` as `election_id`.
- Partition `vote_chain_entry` by `race_id` or timestamp range.

## 6) Invariant Enforcement Mapping (`constraints.md`)

### Vote Election Consistency

Constraint: `Vote.ballot.election == Vote.raceCandidate.race.election`
Enforcement: trigger on `vote` insert that validates both paths resolve to the same election.

### Ballot Precinct Eligibility

Constraint: `Ballot.votingPrecinct` must be assigned to `Ballot.election`
Enforcement: insert validation on `ballot` against current rows in `election_voting_precinct_assignment`.

### VoteChain Uniqueness

Constraint: one active chain per race.
Enforcement: partial unique index on `vote_chain(race_id) WHERE status = 'active'`.

### Access-hash uniqueness

Constraint: one `voter_access_hash` per election.
Enforcement: unique index on `ballot(election_id, voter_access_hash)`.

### One ballot per voter

Not enforced on ledger tables. Those tables have no voter foreign key. The service consults the private issuance mark in [system.md](./system.md) before insert. A partial unique index enforces one `accepted` issuance per person and election.

### One entry per vote, on that race's chain

Constraint: one `vote_chain_entry` per `vote`, and `vote_chain_id` is the chain of the vote's race.
Enforcement: unique index on `vote_chain_entry(vote_id)`, plus an insert trigger that resolves the vote's race to that chain.

### Legal selections and seal time

Constraint: selection rules and the inclusive voting window in [constraints.md](./constraints.md).
Enforcement: service checks before seal, and an insert trigger on `ballot` and `vote` rejects a row that breaks them.

### Canvass statement

Constraint: one statement per `historical` election, matching the closed chains.
Enforcement: unique index on `canvass_statement(election_id)`. The service writes it only while closing the election. Insert-only, same as the ledger tables.

### Race Round Lineage / Method Consistency

Constraint: `round = previousRace.round + 1` and `method = previousRace.method`
Enforcement: insert/update validation on `race` when `previous_race_id` is present.

### Term Non-overlap

Constraint: no overlapping term windows for same seat.
Enforcement: exclusion-style constraint over seat identity and date range.

### Tenancy

Constraint: a row's `tenant_id` matches its parent, and a production election is not `active` without an outside validator.
Enforcement: row-level security on the writer role, `tenant_id = current_setting('app.tenant_id')::uuid`, plus insert triggers that copy `tenant_id` from the parent and reject a mismatch. The service sets `app.tenant_id`. It does not take the tenant from the client. The migration role bypasses row-level security, is logged, and is not used by the API.

## 7) Immutability Rules for Ledger Tables

For `ballot`, `vote`, `vote_chain_entry`:

- Columns are the ledger set: `id`, `created_at`, `created_by`, plus the entity fields. No `updated_at`, `deleted_at`, or `version`.
- Allow `INSERT` only.
- Block `UPDATE` and `DELETE`.
- Do not edit a row to correct it. Do not insert a second ballot to replace it. A voter's later complaint is a `ballot_dispute` row. See [system.md](./system.md).

For `vote_chain`:

- No `deleted_at`. Block `DELETE`.
- Allow `UPDATE` only for the tip columns and `status`.
- A trigger rejects changes to `race_id` and, once set, `initial_entry_id`.
- A trigger rejects `status` moving anywhere except `active` to `closed`.
- A `closed` chain must have `hash` and `signature`.

`canvass_statement` and `restore_incident` are insert-only. Block `UPDATE` and `DELETE`.

On `vote_chain_validation_result`, block changes to the payload, hash, signature, and the columns the payload repeats.

## 8) Operational Notes

- Use migration tooling from day one (Flyway).
- Use separate db roles:
  - app writer role (limited DML, used by the recording service, row-level security on `tenant_id`)
  - public verifier role (`SELECT` on the published-definition tables, chain headers, validator rows, and validation results; `SELECT` on ballot, vote, and chain-entry bodies only when the election is `historical`; no access to `person`, `voter_registration`, issuance, dispute, or passphrase columns; no access to an `address` row that is a person's address)
  - read-only analytics role for officials, still scoped by row-level security to one tenant
  - migration/admin role (bypasses row-level security, logged, not used by the API)
- The recording service and the public lookup service use different roles. Lookup cannot write a ledger row.
- During voting, publish the chain head hash and length through a validator result so the head exists outside this db. Keep write-ahead logs for 35 days in the tenant's region so a restore can reach the latest witnessed head. The backup account is separate from the db account.
- At close, persist each signed head, freeze tally caches, and insert the signed canvass statement. Full ledger bodies, including that statement, become readable to the public verifier role. Keep the locked election snapshot for 22 months, or longer when the agreement says so.
- Restore into quarantine first. Promote a head that matches the latest outside witness. A divergent restore requires a signed `restore_incident` before promotion.
