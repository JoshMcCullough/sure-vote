# AGENTS

## Project Snapshot
- This repository is an early-stage multi-module Gradle Java project. The operating model is written down. Domain behavior is not implemented yet.
- Source of truth for behavior is `docs/system.md`, then `docs/schema.md`, `docs/constraints.md`, and `docs/postgres-strategy.md`. `README.md` is the short entry point. Java layout is `docs/style.md`.

## Module Architecture (important boundaries)
- `entity/`: domain records used by the service and repository.
- `repository/`: persistence. The service is the only writer of ledger rows.
- `service/`: recording, chain append, tally caches, and the rules in `docs/constraints.md`.
- `resource/`: client projections. Public resources must not include private-roll fields (`Person.ssn`, date of birth, voter id, contact, home address, or any voter-to-ballot link).
- `api/`: HTTP. Calls the service and returns resources. Does not re-implement constraint rules and does not expose `entity` types.
- Active modules are declared in `settings.gradle`: `entity`, `resource`, `repository`, `api`, `service`.

## Current Reality
- `entity` and `resource` each have one Lombok base class (`Entity`, `Resource`) for the administrative audit fields. Ledger rows do not use the soft-delete or `updatedAt` fields on that base. See `docs/system.md`.
- `repository`, `api`, and `service` have no main sources yet.
- No test sources exist under `*/src/test/java`.
- Lombok is on `entity` and `resource` only. No Spring, web server, db driver, or Flyway plugin is configured yet.
- `repository`, `api`, and `service` have the JUnit 6 BOM only. No module depends on another yet.

## Build and Test Workflows
- Use Gradle wrapper from repo root (`./gradlew`), not system Gradle.
- Test command: `./gradlew test --console=plain`. Test source sets are empty, so the test task has nothing to run.
- Discover module/tasks quickly with:
  - `./gradlew projects --console=plain`
  - `./gradlew :api:tasks --console=plain`
- Run a single module test cycle with task-qualified paths (example: `./gradlew :repository:test --console=plain`).

## Coding Conventions to Preserve
- Keep layer separation explicit: `api` should not expose `entity` directly; map to `resource` types.
- Add inter-module dependencies explicitly in the consumer module (example in `api/build.gradle`: `implementation project(':entity')`).
- Keep package namespace consistent with Gradle group: `org.sure_vote`.
- Follow `docs/style.md`: 4-space indent, camelCase variables, `final var` where possible, variables at the top of the most enclosing block, opening braces on the same line, `else` on its own line, and no dangling `if`. The existing `Entity` and `Resource` base classes still put the opening brace on the next line.
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
- Prefer smallest viable vertical slice because there is no existing runtime bootstrap yet.
- After edits, run targeted module tests first, then `./gradlew test` at root.

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
