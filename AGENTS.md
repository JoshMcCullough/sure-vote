# AGENTS

## Project Snapshot
- This repository is a multi-module Gradle Java project. The operating model is in `docs/`. In-person and mail-in recording, lookup, issuance, and first-past-the-post, referendum, and ranked-choice tallies run against an in-memory repository. `JdbcSureVoteRepository` implements that same contract on PostgreSQL. Service and HTTP tests use the in-memory store by default. The `:service:postgresTest` task runs the same tests with `-Dsurevote.store=postgres`, one fresh db per `SampleElection`.
- Source of truth for behavior is `docs/system.md`, then `docs/schema.md`, `docs/constraints.md`, and `docs/postgres-strategy.md`. `README.md` is the short entry point. Java layout is `docs/style.md`. `docs/verification.md` is the byte layout, including the rule that a vote's `ballotHash` is the ballot payload hashed with `voteHashes` set to `[]`. `docs/auth-proposal.md` is an unchosen proposal for request identity: WebAuthn sessions for people and signed requests for machines. Do not build it until it is chosen. `docs/ui.md` describes the React app in `ui/`: the public lookup and the workspace that configures an election and runs a showcase demonstration. A production election needs a registered outside validator before activate. A signed `VoteChainValidationResult` witnesses a chain prefix and does not change the tally. A `RestoreIncident` is filed with an operator signature only when a witnessed head is no longer a prefix of the restored chain. It does not change the ledger. Row-level security policies exist in PostgreSQL, but the app connects as the table owner and does not set `app.tenant_id` yet. The React app does not register validators.

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
- The repository implementations are `InMemorySureVoteRepository` and `JdbcSureVoteRepository`. `V3__ledger_guards.sql` holds the immutability triggers: insert-only ledger tables, the chain-header rules, signed validation-result fields, and a fixed `tenant_id`. `V4__references.sql` holds the foreign keys, the parent-tenant check, and the cross-row rules (vote election, ballot precinct assignment, entry chain, outside validator before activate). `PostgresGuardTest` checks them with raw SQL. PostgreSQL tests insert real parent rows through `ParentRows`. A test that needs a state the db forbids is skipped under `postgresTest` with `assumeFalse(SampleStore.onPostgres())`. `V5__tenant_isolation.sql` enables row-level security on `tenant_id` (no `FORCE`, so the owner bypasses it) and makes the trigger functions `security definer`. `JdbcSureVoteRepository.inTenant` sets `app.tenant_id` for one transaction. `V6__system_admins.sql` adds the global `system_admin` table and policies keyed on `app.system_admin_id`, set by `asSystemAdmin`. `SystemAdminService` runs every admin call inside `asSystemAdmin` and checks the actor is an active `SystemAdmin`. `POST /tenants` takes only `showcase`. Service tests create tenants through the fixed `SampleElection.HOST_ADMIN`. `PostgresIsolationTest` checks the policies with a non-owner role it creates. The writer and read-only roles, and the service calling `inTenant` per request, are not done. They wait on how a request names its tenant. Do not add an untested SQL change and describe it as done.
- The service is the only writer of ledger rows. `Sealer` builds an import request and does not persist it. The API maps HTTP to the service and back to resources.
- Mail-in and absentee recording seal an Argon2id `voterAccessHash`, store the viewing passphrase as a sealed box, and write the ballot and an accepted issuance in one transaction after the delivery handoff accepts. A refused handoff rolls that transaction back. The host import accepts an already sealed ballot and does not receive the access code or the passphrase. Ranked-choice close counts instant runoff: a candidate wins when `count * 2 > continuing`, and a tie for last place stops the count. Close writes one operator-signed canvass statement and rolls back when that signature does not match. A new device is rejected once any election for that tenant is not `upcoming`.
- No Spring and no Flyway Gradle plugin. The PostgreSQL driver and Flyway are dependencies of `repository`. Migrations live in `repository/src/main/resources/db/migration`.

## Build and Test Workflows
- Use Gradle wrapper from repo root (`./gradlew`), not system Gradle.
- Java: JDK 25. Every module's Gradle toolchain is `JavaLanguageVersion.of(25)`, and Gradle must run on JDK 25 (wrapper 9.5.0 supports it).
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
- A recorded ballot is final. Do not add a replacement pointer or a superseding ballot. A voter files a `BallotDispute` with the access code. The public response is a count. An official may mark the claim `noted` or `dismissed`. The dispute does not change the ballot, the chain, or the tally, and the access code is not stored on it.
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
- `docs/ui.md`
- `settings.gradle`
- `build.gradle`
- `api/build.gradle`
- `entity/build.gradle`
- `repository/build.gradle`
- `resource/build.gradle`
- `service/build.gradle`
