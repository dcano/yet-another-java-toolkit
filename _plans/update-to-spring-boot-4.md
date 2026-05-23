# Implementation Plan: Update to Spring Boot 4

Spec: [_specs/update-to-spring-boot-4.md](../_specs/update-to-spring-boot-4.md)
Branch: `feature/update-to-spring-boot-4`

## Pre-flight summary

| Item | Finding |
|---|---|
| Current Spring Boot | `3.5.10` — already at latest 3.5.x; prep phase complete, can bump directly |
| Java | `21` — Boot 4 requires 17+; no Java upgrade needed |
| `@MockBean`/`@SpyBean` | Not present — no action |
| Undertow | Not used — no action |
| `@Mock` + `MockitoExtension` | Already correct pattern — no action |
| Unused deps | `hypersistence-utils-hibernate-62` (declared, zero Java imports), `springdoc-openapi.version` (property only, zero `<dependency>` refs) |
| Jackson 2 prod imports | 3 files in `toolkit-core`, `toolkit-event-store-jdbc-postgres`, `toolkit-autoconfiguration` |
| Jackson 2 test imports | 5 files across `toolkit-outbox-jpa-postgres`, `toolkit-event-store-jdbc-postgres` |
| `@EntityScan` package | Moved in Boot 4: `org.springframework.boot.autoconfigure.domain` → `org.springframework.boot.persistence.autoconfigure` |
| Auto-config `.imports` file | Missing (resources dir empty); `@Configuration` not `@AutoConfiguration` — investigate intent |

---

## Phase 1 — Root POM: version bumps and cleanup

**File: `pom.xml`**

### 1.1 Version property updates

| Property | Before | After |
|---|---|---|
| `spring-boot.version` | `3.5.10` | `4.0.6` |
| `debezium.version` | `3.4.1.Final` | `3.5.1.Final` |
| `hypersistence-utils-hibernate.version` | `3.7.4` | **remove** (dependency is unused) |
| `springdoc-openapi.version` | `2.8.15` | **remove** (never used as a dependency) |

### 1.2 Remove stale `<dependencyManagement>` entries

Remove each of the following managed entries. The Spring Boot 4 BOM manages the Jakarta EE 11 versions correctly; the explicit overrides either pin stale versions or are for unused libraries:

- `io.hypersistence:hypersistence-utils-hibernate-62` (lines 119–122) — library unused
- `org.springframework.boot:spring-boot-starter-security` explicit version entry (lines 97–100) — already in BOM; explicit version risks mismatch after the BOM upgrade
- `jakarta.validation:jakarta.validation-api` at `3.0.2` — Boot 4 BOM provides Jakarta EE 11 Validation 3.1.x; remove override
- `jakarta.transaction:jakarta.transaction-api` at `2.0.1` — let the BOM own it
- `org.glassfish:jakarta.el` at `4.0.2` — let the BOM provide the EL 6 implementation

---

## Phase 2 — Remove unused `hypersistence-utils` from outbox module

**File: `toolkit-outbox-jpa-postgres/pom.xml`**

Remove the dependency block:
```xml
<!-- REMOVE -->
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-62</artifactId>
</dependency>
```

`OutboxMessageEntity` uses native Hibernate 6/7 annotations (`@JdbcTypeCode`, `SqlTypes`). No `io.hypersistence` import exists in any Java file.

---

## Phase 3 — Add `spring-boot-properties-migrator` (temporary)

**File: `toolkit-state-propagation-debezium-runtime/pom.xml`**

Add temporarily to surface any renamed/removed properties in `application.yaml`:

```xml
<!-- TEMPORARY — remove before final commit -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```

This will print diagnostics at startup and temporarily alias renamed keys so the app can still start. Review `application.yaml` against the migrator output, fix any renames, then remove this dependency in Phase 10.

---

## Phase 4 — Starter renames and additions

### 4.1 `toolkit-state-propagation-debezium-runtime/pom.xml`

`spring-boot-starter-web` is deprecated in Boot 4; replace with the modular name:

```xml
<!-- Before -->
<artifactId>spring-boot-starter-web</artifactId>

<!-- After -->
<artifactId>spring-boot-starter-webmvc</artifactId>
```

Add test starters for each main starter present. Under Boot 4, without test starters, slice tests and MockMVC support won't be available:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 4.2 `toolkit-outbox-jpa-postgres/pom.xml`

Add the JPA test starter (replaces `spring-boot-starter-test`; it brings `spring-boot-starter-test` transitively):

```xml
<!-- Add -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
```

Remove the now-redundant standalone `spring-boot-starter-test` declaration if present, or keep it if needed for non-JPA test slice scenarios.

Also remove the now-BOM-managed test dependencies that were previously overridden:
- `jakarta.validation:jakarta.validation-api` (test scope, line 43–46) — BOM owns it
- `org.glassfish:jakarta.el` (test scope, line 47–50) — BOM owns the EL 6 impl

### 4.3 `toolkit-event-store-jdbc-postgres/pom.xml`

`flyway-core` is declared directly (test scope) — a "naked" dep. Under Boot 4, `flyway-core` alone no longer triggers auto-configuration; the starter is also required:

```xml
<!-- Add -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway-test</artifactId>
    <scope>test</scope>
</dependency>
```

The existing `flyway-core` and `flyway-database-postgresql` entries can remain or be removed in favour of letting the starter manage them.

Also add the JDBC test starter:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Phase 5 — Jackson 2 → Jackson 3 migration

Boot 4 ships Jackson 3. Package/group changes: `com.fasterxml.jackson.core.*` and `com.fasterxml.jackson.databind.*` → `tools.jackson.core.*` / `tools.jackson.databind.*`. The `jackson-annotations` package (`com.fasterxml.jackson.annotation.*`) is **unchanged**.

Jackson 3 `ObjectMapper` is replaced by `JsonMapper` with a builder pattern:
```java
// Before
ObjectMapper mapper = new ObjectMapper();
mapper.findAndRegisterModules();

// After
JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
```

### 5.1 `toolkit-core/pom.xml`

```xml
<!-- Before -->
<groupId>com.fasterxml.jackson.core</groupId>
<artifactId>jackson-databind</artifactId>

<!-- After -->
<groupId>tools.jackson.core</groupId>
<artifactId>jackson-databind</artifactId>
```

### 5.2 `toolkit-core/src/main/java/io/twba/tk/core/DomainEventAppender.java`

```java
// Before
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
// ...
private final ObjectMapper objectMapper;
public DomainEventAppender(Outbox outbox, ObjectMapper objectMapper, ...)

// After
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
// ...
private final JsonMapper objectMapper;
public DomainEventAppender(Outbox outbox, JsonMapper objectMapper, ...)
```

Update the `catch (JsonProcessingException e)` block to `catch (JacksonException e)`.

### 5.3 `toolkit-event-store-jdbc-postgres/pom.xml`

```xml
<!-- Before -->
<groupId>com.fasterxml.jackson.core</groupId>
<artifactId>jackson-databind</artifactId>

<!-- Before -->
<groupId>com.fasterxml.jackson.datatype</groupId>
<artifactId>jackson-datatype-jsr310</artifactId>

<!-- After -->
<groupId>tools.jackson.core</groupId>
<artifactId>jackson-databind</artifactId>

<!-- After (verify exact coordinates for Jackson 3) -->
<groupId>tools.jackson.datatype</groupId>
<artifactId>jackson-datatype-jsr310</artifactId>
```

> **Verify**: run `./mvnw dependency:tree -pl toolkit-event-store-jdbc-postgres` after the root POM bump to confirm the `tools.jackson.datatype:jackson-datatype-jsr310` artifact exists. If JSR-310 support is now bundled in Jackson 3 core, remove the explicit dependency.

### 5.4 `toolkit-event-store-jdbc-postgres/src/main/java/io/twba/tk/eventsource/EventStoreJdbcPostgres.java`

All 7 Jackson imports need updating:

```java
// Before
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

// After
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonDeserializer;
import tools.jackson.databind.JsonSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializerProvider;
import tools.jackson.databind.module.SimpleModule;
```

Update `ObjectMapper` type references to `JsonMapper` throughout the class. Update any `throws JsonProcessingException` in method signatures or catch blocks to `throws JacksonException` / `catch (JacksonException e)`.

### 5.5 `toolkit-autoconfiguration/src/main/java/io/twba/tk/autoconfigure/ToolkitAutoconfiguration.java`

```java
// Before
import com.fasterxml.jackson.databind.ObjectMapper;
// ...
public EventStore jdbcPostgresEventStore(DataSource dataSource, ObjectMapper objectMapper)

// After
import tools.jackson.databind.json.JsonMapper;
// ...
public EventStore jdbcPostgresEventStore(DataSource dataSource, JsonMapper objectMapper)
```

Spring Boot 4 auto-configures a `JsonMapper` bean (not `ObjectMapper`), so the parameter type must match. Also update the call to `new EventStoreJdbcPostgres(dataSource, objectMapper)` if the `EventStoreJdbcPostgres` constructor signature changed in step 5.4.

### 5.6 Test files — Jackson 2 → Jackson 3

Apply the same import replacements to the following test files:

| File | Changes needed |
|---|---|
| `toolkit-event-store-jdbc-postgres/src/test/.../EventStoreJdbcPostgresTest.java` | `ObjectMapper` → `JsonMapper`; update instantiation to `JsonMapper.builder().build()` |
| `toolkit-outbox-jpa-postgres/src/test/.../OutboxMessages.java` | `ObjectMapper` → `JsonMapper`; `JsonProcessingException` → `JacksonException`; update instantiation |
| `toolkit-outbox-jpa-postgres/src/test/.../OutboxJpaTest.java` | `JsonProcessingException` → `JacksonException` |
| `toolkit-outbox-jpa-postgres/src/test/.../OutboxJpaIT.java` | `JsonProcessingException` → `JacksonException` |
| `toolkit-outbox-jpa-postgres/src/test/.../TestAppConfig.java` | `ObjectMapper` → `JsonMapper`; update instantiation |

---

## Phase 6 — Package move: `@EntityScan`

**File: `toolkit-outbox-jpa-postgres/src/main/java/io/twba/tk/cdc/outbox/config/PersistenceConfigOutbox.java`**

```java
// Before
import org.springframework.boot.autoconfigure.domain.EntityScan;

// After
import org.springframework.boot.persistence.autoconfigure.EntityScan;
```

---

## Phase 7 — Verify `spring-boot-autoconfigure` in `toolkit-autoconfiguration`

**File: `toolkit-autoconfiguration/pom.xml`**

After the root POM bump, verify that `spring-boot-autoconfigure` still resolves. In Boot 4, this artifact continues to exist (as a compatibility façade) but the internal packages have moved into per-module jars.

If compilation fails for `ToolkitAutoconfiguration.java` (e.g. `@ConditionalOnProperty` not found), replace the dependency with the specific module that contains the conditional annotations:

```xml
<!-- If spring-boot-autoconfigure no longer resolves, replace with: -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-condition</artifactId>
</dependency>
```

**Auto-configuration registration (optional but recommended):** The resources directory is empty and the class uses `@Configuration` rather than `@AutoConfiguration`. If consumers are expected to auto-discover this module via Spring Boot's autoconfiguration mechanism, create:

```
toolkit-autoconfiguration/src/main/resources/META-INF/spring/
  org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

with content:
```
io.twba.tk.autoconfigure.ToolkitAutoconfiguration
```

And change the annotation from `@Configuration` to `@AutoConfiguration`. Confirm with current usage before applying.

---

## Phase 8 — First compile pass

```bash
./mvnw clean install -DskipTests
```

Fix any remaining compilation errors. Common Boot 4 causes not already covered above:
- Any `javax.annotation.*` / `javax.inject.*` that may have crept in → `jakarta.*`
- `ListenableFuture` → `CompletableFuture` (if present)
- Any use of removed Boot APIs

---

## Phase 9 — Full integration test run

```bash
./mvnw clean verify
```

This runs the Testcontainers-backed integration tests. Expected suites:
- `toolkit-outbox-jpa-postgres`: `OutboxJpaIT` (PostgreSQL) and `OutboxJpaTest` (Mockito)
- `toolkit-event-store-jdbc-postgres`: `EventStoreJdbcPostgresTest` (PostgreSQL + Flyway)
- `toolkit-cdc-debezium-postgres`: CDC integration tests (PostgreSQL)

If tests fail, common Boot 4 runtime causes to check:
- `@SpringBootTest` no longer provides MockMVC automatically → add `@AutoConfigureMockMvc` if needed
- CSRF blocking POSTs → see `references/security.md`; REST APIs should disable CSRF
- Auto-configuration not triggering → missing starter or `.imports` file
- Spring AMQP retry customizer renamed → `RabbitRetryTemplateCustomizer` is gone

---

## Phase 10 — Cleanup

1. Remove the `spring-boot-properties-migrator` dependency added in Phase 3 from `toolkit-state-propagation-debezium-runtime/pom.xml`.
2. Review the migrator's startup output — fix any remaining renamed/removed property keys in `application.yaml`.
3. Confirm there are zero `com.fasterxml.jackson.core.*` or `com.fasterxml.jackson.databind.*` imports remaining (`com.fasterxml.jackson.annotation.*` is expected and correct).

---

## Phase 11 — Final clean build

```bash
./mvnw clean verify
```

All modules must build and all tests must pass before the branch is ready for review.

---

## Complete file change index

| File | Phase | What changes |
|---|---|---|
| `pom.xml` | 1 | Bump `spring-boot.version`, `debezium.version`; remove unused properties + managed entries |
| `toolkit-outbox-jpa-postgres/pom.xml` | 2, 4.2 | Remove `hypersistence-utils`; add JPA test starter; remove overridden Jakarta deps |
| `toolkit-state-propagation-debezium-runtime/pom.xml` | 3, 4.1, 10 | Add/remove `properties-migrator`; rename `web` → `webmvc` starter; add test starters |
| `toolkit-event-store-jdbc-postgres/pom.xml` | 4.3, 5.3 | Add Flyway and JDBC test starters; update Jackson coordinates |
| `toolkit-core/pom.xml` | 5.1 | Update Jackson 2 `com.fasterxml` → Jackson 3 `tools.jackson` coordinates |
| `toolkit-core/.../DomainEventAppender.java` | 5.2 | `ObjectMapper` → `JsonMapper`; `JsonProcessingException` → `JacksonException` |
| `toolkit-event-store-jdbc-postgres/.../EventStoreJdbcPostgres.java` | 5.4 | All 7+ Jackson imports → `tools.jackson.*`; `ObjectMapper` → `JsonMapper` |
| `toolkit-autoconfiguration/.../ToolkitAutoconfiguration.java` | 5.5 | `ObjectMapper` → `JsonMapper` parameter type |
| `toolkit-event-store-jdbc-postgres/test/.../EventStoreJdbcPostgresTest.java` | 5.6 | `ObjectMapper` → `JsonMapper` |
| `toolkit-outbox-jpa-postgres/test/.../OutboxMessages.java` | 5.6 | `ObjectMapper` → `JsonMapper`; `JsonProcessingException` → `JacksonException` |
| `toolkit-outbox-jpa-postgres/test/.../OutboxJpaTest.java` | 5.6 | `JsonProcessingException` → `JacksonException` |
| `toolkit-outbox-jpa-postgres/test/.../OutboxJpaIT.java` | 5.6 | `JsonProcessingException` → `JacksonException` |
| `toolkit-outbox-jpa-postgres/test/.../TestAppConfig.java` | 5.6 | `ObjectMapper` → `JsonMapper` |
| `toolkit-outbox-jpa-postgres/.../PersistenceConfigOutbox.java` | 6 | `@EntityScan` import package move |
| `toolkit-autoconfiguration/pom.xml` | 7 | Verify `spring-boot-autoconfigure`; optionally add `.imports` file + `@AutoConfiguration` |
