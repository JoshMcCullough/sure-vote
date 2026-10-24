# AGENTS

## Project Snapshot
- This repository is a multi-module Gradle Java project. The operating model is in `docs/`. The in-person recording path, lookup, issuance, and first-past-the-post and referendum tallies run against an in-memory repository.
- Source of truth for behavior is `docs/system.md`, then `docs/schema.md`, `docs/constraints.md`, and `docs/postgres-strategy.md`. `README.md` is the short entry point. Java layout is `docs/style.md`. `docs/verification.md` is the byte layout, including the rule that a vote's `ballotHash` is the ballot payload hashed with `voteHashes` set to `[]`.

## Module Architecture (important boundaries)
- `entity/`: domain records used by the service and repository.
- `repository/`: persistence. The service is the only writer of ledger rows.
- `service/`: recording, chain append, tally caches, and the rules in `docs/constraints.md`.
- `resource/`: client projections. Public resources must not include private-roll fields (`Person.ssn`, date of birth, voter id, contact, home address, or any voter-to-ballot link).
- `api/`: HTTP. Calls the service and returns resources. Does not re-implement constraint rules and does not expose `entity` types.
- Active modules are declared in `settings.gradle`: `style-checks`, `entity`, `resource`, `repository`, `api`, `service`.
- `repository` depends on `entity`. `service` depends on `entity` and `repository`. `api` depends on `entity`, `repository`, `resource`, and `service`. `style-checks` is a Checkstyle dependency, not an application module.

## Current Reality
- `Entity` is a hand-written administrative base. Ledger rows use `LedgerEntity` and do not use soft-delete or `updatedAt`. See `docs/system.md`.
- Lombok remains only on `resource` (`Resource`). Do not put it back on `entity`. New API resources do not extend `Resource`, so Jackson does not emit audit fields.
- The repository implementation is `InMemorySureVoteRepository`. It is the store the service and tests use. Do not add an untested SQL or Flyway path and describe it as done.
- The service is the only writer of ledger rows. `Sealer` builds an import request and does not persist it. The API maps HTTP to the service and back to resources.
- Mail-in and absentee import throw "mail-in recording is not implemented yet". Ranked-choice close counts instant runoff: a candidate wins when `count * 2 > continuing`, and a tie for last place stops the count. The canvass statement is not written. A new device is rejected once any election for that tenant is not `upcoming`.
- No Spring, db driver, or Flyway plugin is configured.

## Build and Test Workflows
- Use Gradle wrapper from repo root (`./gradlew`), not system Gradle.
- Check command: `./gradlew check --console=plain`. That runs Checkstyle, the tests, and the JaCoCo report. Checkstyle is `14.3.0` with `config/checkstyle/checkstyle.xml` and `maxWarnings = 0`. The `checkstyle` configuration needs both the Checkstyle tool jar and `project(':style-checks')`. The aggregate coverage report is `testCodeCoverageReport`. `check` also runs `coverageVerification`, which fails below 85% instruction coverage on that same aggregate. The minimum is 85% because unused schema types stay in the denominator.
- Run a single module with a task-qualified path, for example `./gradlew :service:test --console=plain`.

## Coding Conventions to Preserve
- Keep layer separation explicit: `api` should not expose `entity` directly; map to `resource` types.
- Add inter-module dependencies explicitly in the consumer module (example in `api/build.gradle`: `implementation project(':entity')`).
- Keep package namespace consistent with Gradle group: `org.sure_vote`.
- Follow `docs/style.md`: 4-space indent, camelCase variables, `final var` where possible, variables at the top of the most enclosing block, opening braces on the same line, `else` on its own line, and no dangling `if`. A chain of two calls may stay on one line. A longer chain puts the receiver on its own line and each call on the next line, starting with `.`. Checkstyle enforces this, including tests.
- Mirror source layout per module: `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`.

## Practical Agent Guidance
- When adding a feature, scaffold in this order: `entity` -> `repository` -> `service` -> `resource` -> `api`.
- Ballot recording checks `BallotIssuance` in the same transaction and does not store the ballot id or the access code on that row. The public ledger cannot prove one ballot per voter.
- The access code is the voter-facing secret, shown as a QR code and as text. `voterAccessHash` stores a hash of it. Do not put the stored hash, or the ballot integrity hash, in the QR payload. Do not write the plaintext access code to the db. Mail-in lookup also requires the registration viewing passphrase. Polling-place voting machines stay offline. Payload bytes are specified in `docs/verification.md`.
- Geographic results are one signed canvass statement recomputed from each race chain. Do not add a rollup chain per precinct, district, locality, or nation.
- A pollbook may void a `BallotIssuance` only before the voter starts a machine session. Do not store a session ticket on the ballot. The sealed ballot is the ballot of record. `absentee` uses the same path as `mail-in`.
- The showcase deployment must not collect real voter rolls or SSNs. Product goals are in `README.md` and `docs/system.md`.
- The hosted service is multi-tenant. Sure Vote Systems is the copyright holder and the normal host. A small locality is a tenant, not its own deployment. Every tenant-owned row carries `tenant_id`. On a production tenant the host does not hold operator, import, viewing, or device private keys. Hosting rules are in `docs/system.md`.
- A recorded ballot is final. Do not add a replacement pointer or a superseding ballot. A `BallotDispute` does not change ledger rows.
- The repository is source-available under `LICENSE`, not an open-source license. Chain verification is specified in `docs/verification.md` and must not require running this codebase.
- If you introduce a framework (e.g., Spring), document it in `README.md` and update only relevant module `build.gradle` files.
- Prefer the smallest change that keeps the in-person path and its tests working.
- After edits, run targeted module tests first, then `./gradlew check` at the root.

## Terminology
- **Database**: shorten as "db", not "DB" or "Db" (unless capitalizing due to sentence start).

## Key Files to Read First
- `README.md`
- `LICENSE`
- `docs/system.md`
- `docs/verification.md`
- `docs/schema.md`
- `docs/constraints.md`
- `docs/postgres-strategy.md`
- `docs/style.md`
- `settings.gradle`
- `build.gradle`
- `api/build.gradle`
- `entity/build.gradle`
- `repository/build.gradle`
- `resource/build.gradle`
- `service/build.gradle`
