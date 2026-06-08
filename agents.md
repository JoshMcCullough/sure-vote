# AGENTS

## Project Snapshot
- This repository is an early-stage multi-module Gradle Java project with **structure in place but no domain code yet**.
- Source of truth for intent is `README.md` (module responsibilities) plus Gradle files in root and each module.

## Module Architecture (important boundaries)
- `resource/`: API-facing DTOs/resources returned to clients (`README.md`).
- `entity/`: internal domain entities used by business logic/storage (`README.md`).
- `repository/`: persistence abstraction layer (`README.md`).
- `api/`: endpoint layer that should orchestrate repository + entity and return resource models (`README.md`).
- Active modules are declared in `settings.gradle`: `entity`, `resource`, `repository`, `api`.

## Current Reality (before coding)
- No Java/Kotlin sources currently exist under `*/src/main/java` or tests under `*/src/test/java`.
- No framework dependencies are configured yet (no Spring, no web server, no DB driver).
- Each module currently has identical minimal `build.gradle` with JUnit 6 BOM + Jupiter.

## Build and Test Workflows
- Use Gradle wrapper from repo root (`./gradlew`), not system Gradle.
- Verified baseline command: `./gradlew test --console=plain` (currently reports `NO-SOURCE` for all modules).
- Discover module/tasks quickly with:
  - `./gradlew projects --console=plain`
  - `./gradlew :api:tasks --console=plain`
- Run a single module test cycle with task-qualified paths (example: `./gradlew :repository:test --console=plain`).

## Coding Conventions to Preserve
- Keep layer separation explicit: `api` should not expose `entity` directly; map to `resource` types.
- Add inter-module dependencies explicitly in the consumer module (example in `api/build.gradle`: `implementation project(':entity')`).
- Keep package namespace consistent with Gradle group: `org.sure_vote`.
- Mirror source layout per module: `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources`.

## Practical Agent Guidance
- When adding a feature, scaffold in this order: `entity` -> `repository` -> `resource` -> `api`.
- If you introduce a framework (e.g., Spring), document it in `README.md` and update only relevant module `build.gradle` files.
- Prefer smallest viable vertical slice because there is no existing runtime bootstrap yet.
- After edits, run targeted module tests first, then `./gradlew test` at root.

## Key Files to Read First
- `README.md`
- `settings.gradle`
- `build.gradle`
- `api/build.gradle`
- `entity/build.gradle`
- `repository/build.gradle`
- `resource/build.gradle`

