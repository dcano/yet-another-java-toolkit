# Spec for update-to-spring-boot-4

branch: feature/update-to-spring-boot-4

## Summary

Migrate all toolkit modules from Spring Boot 3.5.10 to Spring Boot 4.0.6, following the official migration path. The project is already on the latest 3.5.x baseline, so the prep step is complete. The migration encompasses: starter renames and the new modular auto-configuration structure, a full Jackson 2 → Jackson 3 (`tools.jackson.*`) migration across 4 modules, the `@EntityScan` package move, a Debezium version bump to 3.5.1.Final, and cleanup of stale/unused POM entries.

## Functional Requirements

- Bump `spring-boot.version` in the root POM to `4.0.6`.
- Bump `debezium.version` to `3.5.1.Final`.
- Rename `spring-boot-starter-web` to `spring-boot-starter-webmvc` in `toolkit-state-propagation-debezium-runtime`.
- Add the corresponding test starters for every module that declares a Boot starter (modular strategy).
- Migrate all `com.fasterxml.jackson.*` imports (except `jackson-annotations`) to `tools.jackson.*` across all affected modules — this covers both production and test source files in `toolkit-core`, `toolkit-event-store-jdbc-postgres`, `toolkit-autoconfiguration`, and `toolkit-outbox-jpa-postgres`.
- Update the Jackson Maven coordinates in `toolkit-core/pom.xml` and `toolkit-event-store-jdbc-postgres/pom.xml` from `com.fasterxml.jackson.*` group to `tools.jackson.*`.
- Fix the `@EntityScan` import in `PersistenceConfigOutbox.java`: `org.springframework.boot.autoconfigure.domain` → `org.springframework.boot.persistence.autoconfigure`.
- Remove the unused `hypersistence-utils-hibernate-62` dependency from `toolkit-outbox-jpa-postgres` and its managed entry from the root POM (no Java imports exist for this library).
- Remove the unused `springdoc-openapi.version` property from the root POM (declared but never referenced in any `<dependency>` block).
- Remove the explicit `spring-boot-starter-security` version override in root POM `<dependencyManagement>` (already managed by the Spring Boot BOM; the override risks pinning a stale version).
- Add `spring-boot-properties-migrator` temporarily to `toolkit-state-propagation-debezium-runtime` to surface renamed/removed properties; remove before the final commit.
- Verify `spring-boot-autoconfigure` in `toolkit-autoconfiguration` resolves correctly under Spring Boot 4's modular structure.
- Run `./mvnw clean verify` successfully across the full multi-module build with all Testcontainers integration tests passing.

## Confirmed Non-Issues

- `@MockBean`/`@SpyBean` — **not used** anywhere in the codebase. No change needed.
- `javax.sql.DataSource` — **Java SE JDBC API**, not Jakarta EE. Stays as-is.
- Undertow — **not used** in this project.
- `@Mock` + `@ExtendWith(MockitoExtension.class)` — **already correct** for Boot 4; `MockitoTestExecutionListener` removal does not affect this project.
- Existing Flyway migration scripts — Flyway 10.x (managed by the BOM) supports the existing V1 scripts unchanged.

## Possible Edge Cases

- `spring-boot-autoconfigure` may be modularized in Boot 4; the `toolkit-autoconfiguration` module depends on it directly. If the jar is removed or renamed, the conditional annotations (`@ConditionalOnProperty`, `@ConditionalOnBean`, etc.) need to be sourced from the new module path.
- `toolkit-autoconfiguration/src/main/resources` is empty — there is no `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file. The class uses `@Configuration` (not `@AutoConfiguration`) and is presumably imported explicitly by consumers. Verify this is still the expected consumption pattern; if auto-discovery is wanted, add the `.imports` file and switch to `@AutoConfiguration`.
- Jackson 3 `JsonMapper` construction is immutable (builder-style). Any place that calls `new ObjectMapper()` must switch to `JsonMapper.builder().build()`.
- `jackson-datatype-jsr310` group/artifact may have changed in Jackson 3 (`tools.jackson.datatype:jackson-datatype-jsr310`). Verify the exact coordinates and whether JSR310 support is now bundled by default.
- Spring AMQP retry API changed: `RabbitRetryTemplateCustomizer` → `RabbitTemplateRetrySettingsCustomizer` / `RabbitListenerRetrySettingsCustomizer`. Check if any code in `toolkit-message-publisher-rabbitmq` or the runtime uses the old type.
- `toolkit-event-store-jdbc-postgres` declares `flyway-core` (test scope) directly — a "naked" dependency. Under Boot 4, `spring-boot-starter-flyway` is also required for Flyway to be auto-configured in tests.

## Acceptance Criteria

- `./mvnw clean verify` passes across all modules with no compilation errors or test failures.
- No `com.fasterxml.jackson.core.*` or `com.fasterxml.jackson.databind.*` imports remain in production or test sources (annotations package `com.fasterxml.jackson.annotation` is expected to stay).
- `spring-boot-starter-web` is replaced by `spring-boot-starter-webmvc` in the runtime module.
- `@EntityScan` resolves from `org.springframework.boot.persistence.autoconfigure`.
- `hypersistence-utils-hibernate-62` is absent from all POM files.
- `spring-boot-properties-migrator` is absent from all POM files in the final commit.
- The `toolkit-state-propagation-debezium-runtime` Spring Boot app starts without errors.

## Open Questions (resolved)

- **Spring Boot 4 GA version**: use `4.0.6` per start.spring.io.
- **Debezium version**: use `3.5.1.Final`.
- **Backwards compatibility with Boot 3.x**: clean cut-over, no compatibility shim needed.
- **`distributionManagement` placeholder**: out of scope.
- **`@MockBean` / Undertow**: confirmed not present in codebase, no action needed.

## Testing Guidelines

Existing tests in each module cover the relevant functionality. No new test files are required beyond ensuring the existing integration tests pass under the new versions. Key tests to keep green:

- `toolkit-outbox-jpa-postgres`: `OutboxJpaIT` (Testcontainers/PostgreSQL) and `OutboxJpaTest` (Mockito unit test).
- `toolkit-event-store-jdbc-postgres`: `EventStoreJdbcPostgresTest` (Testcontainers/PostgreSQL + Flyway).
- `toolkit-cdc-debezium-postgres`: existing Testcontainers integration tests.
- Any tests in `toolkit-autoconfiguration` that verify conditional bean wiring.
