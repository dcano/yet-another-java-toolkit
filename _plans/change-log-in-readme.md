# Plan: Change Log in README

Spec: [`_specs/change-log-in-readme.md`](../_specs/change-log-in-readme.md)
Branch: `feature/change-log-in-readme`

## Goal

Append a `## Changelog` section at the bottom of `README.md` covering all six released versions, in reverse chronological order, using the `Added / Changed / Fixed / Breaking Changes` category structure. No snapshot versions. No new files — the change lives entirely in `README.md`.

---

## Release inventory (from git history)

| Version | Date | Source commits / PRs |
|---------|------|----------------------|
| v2.1.0 | 2026-06-21 | PR #5 — `fix: delete outbox rows after relay and use durable JDBC offset storage`; `percentile publication in domain query handler timer` |
| v2.0.0 | 2026-06-14 | PR #4 — `improved rabbitmq reliability`; `percentile in command execution metrics`; `improvements round 1 details` |
| v1.0.0 | 2026-05-23 | PR #3 — `feat!: migrate to Spring Boot 4.0.6 and Jackson 3` (**breaking**) |
| v0.0.3 | 2026-05-09 | PR #2 — `Add domain query support` |
| v0.0.2 | 2026-05-08 | PR #1 — `command execution instrumentation` |
| v0.0.1 | 2026-05-02 | Initial release — core outbox, event store, CDC, CQRS command bus |

---

## Steps

### Step 1 — Draft the changelog content

Map each release to its categories based on the commit log above:

**v2.1.0 — 2026-06-21**
- Fixed: delete outbox rows from the database after successful relay to avoid unbounded table growth
- Fixed: use durable JDBC-backed Debezium offset storage to survive restarts without replaying already-relayed events
- Added: percentile publication for `twba.query.execution` timer metrics

**v2.0.0 — 2026-06-14**
- Added: AMQP reliability improvements — publisher returns callback (`cdc.publisher-returns`), dead-letter queue support via `ToolkitQueueBuilder`
- Added: `EventRegistry` / `@EventDefinition` consumer-side wiring with `CloudEventMessageConverter`
- Added: percentile publication for `twba.command.execution` timer metrics

**v1.0.0 — 2026-05-23**
- Breaking Changes: migrated runtime to Spring Boot 4.0.6 and Jackson 3 — consuming services must upgrade their Spring Boot version and Jackson dependencies

**v0.0.3 — 2026-05-09**
- Added: domain query support — `QueryBus`, `QueryHandler`, and `twba.query.execution` timer

**v0.0.2 — 2026-05-08**
- Added: command execution instrumentation — `twba.command.execution` Micrometer timer, enabled via `io.twba.tk.properties.instrumentation.enabled=true`

**v0.0.1 — 2026-05-02**
- Added: initial release — outbox pattern (JPA/PostgreSQL), JDBC event store, CDC via embedded Debezium, CQRS command bus with decorator pipeline, Spring Boot auto-configuration, Flyway-managed schemas

### Step 2 — Append the section to README.md

Append the `## Changelog` section after the `## License` section (current last section, line 454). Use the format:

```markdown
## Changelog

### v2.1.0 — 2026-06-21
#### Fixed
- ...
#### Added
- ...

### v2.0.0 — ...
```

No links to GitHub releases are added in this iteration (open question in the spec — defer until decided).

### Step 3 — Update CLAUDE.md (already done)

`CLAUDE.md` already has the instruction: _"A change log must be kept up to date in the project readme."_ No further changes needed there.

---

## Acceptance criteria check

| Criterion | How it's met |
|-----------|--------------|
| `README.md` contains `## Changelog` section | Appended in Step 2 |
| All released versions have an entry | All 6 tags covered |
| Entries newest-first | v2.1.0 → v0.0.1 order |
| Added / Changed / Fixed / Breaking Changes categories | Applied per release; empty categories omitted |
| No snapshot versions | Only tagged releases included |
| Accurate against actual release content | Derived directly from PR merge commits and `feat!` messages |

---

## Out of scope

- Extracting to a separate `CHANGELOG.md` file (open question — not decided)
- Adding links to GitHub release pages (open question — not decided)
- Any code changes — this is a documentation-only change
