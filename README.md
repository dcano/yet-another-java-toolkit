# java-toolkit

A Spring Boot multi-module toolkit for building event-driven microservices with event sourcing, CQRS, and CDC-based state propagation over PostgreSQL and RabbitMQ.

[![Build and Publish](https://github.com/YOUR_GITHUB_USERNAME/java-toolkit/actions/workflows/publish.yml/badge.svg)](https://github.com/YOUR_GITHUB_USERNAME/java-toolkit/actions/workflows/publish.yml)

## Overview

java-toolkit provides a coherent set of infrastructure libraries for the `io.twba` ecosystem. It implements the **Outbox Pattern** for transactional event publishing, **Event Sourcing** with a JDBC-backed event store, **CQRS** command dispatching with decorator-based pipelines, and **Change Data Capture** via an embedded Debezium engine. The toolkit is designed for Java 21 and Spring Boot 3.5, and targets PostgreSQL as the persistence layer with RabbitMQ as the message broker.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `toolkit-core` | `io.twba:toolkit-core` | Domain model abstractions, command bus, event buffering, outbox/CDC interfaces |
| `toolkit-outbox-jpa-postgres` | `io.twba:toolkit-outbox-jpa-postgres` | JPA/Hibernate outbox table implementation with Flyway migrations |
| `toolkit-event-store-jdbc-postgres` | `io.twba:toolkit-event-store-jdbc-postgres` | JDBC event store for PostgreSQL |
| `toolkit-cdc-debezium-postgres` | `io.twba:toolkit-cdc-debezium-postgres` | Embedded Debezium CDC relay from outbox table |
| `toolkit-message-publisher-rabbitmq` | `io.twba:toolkit-message-publisher-rabbitmq` | RabbitMQ CloudEvents publisher |
| `toolkit-tx-spring` | `io.twba:toolkit-tx-spring` | Spring `PlatformTransactionManager` adapter |
| `toolkit-security-adapters` | `io.twba:toolkit-security-adapters` | Property-based service authenticator |
| `toolkit-autoconfiguration` | `io.twba:toolkit-autoconfiguration` | Spring Boot auto-configuration for the toolkit |
| `toolkit-state-propagation-debezium-runtime` | _(runnable)_ | Standalone CDC service — reads outbox, publishes to RabbitMQ |
| `toolkit-state-propagation-performance-test` | _(runnable)_ | Load-testing consumer for the CDC pipeline |

## Features

- **Outbox Pattern** — events are written to an `outbox` table within the same database transaction as the business change, eliminating dual-write races.
- **Event Sourcing** — aggregates are rebuilt by replaying an ordered stream of immutable domain events from the JDBC event store.
- **CQRS command bus** — decorator pipeline (transactional, event enrichment, event publishing) wraps each `CommandHandler` without coupling business logic to infrastructure.
- **Embedded Debezium CDC** — outbox rows captured via PostgreSQL logical replication (`pgoutput`), converted to CloudEvents, and relayed to RabbitMQ without polling.
- **CloudEvents compliance** — all published messages use the [CloudEvents 3.0](https://cloudevents.io) format over AMQP.
- **Multi-tenancy** — `TenantId` is propagated through commands, events, and outbox rows.
- **Spring Boot auto-configuration** — declare a dependency on `toolkit-autoconfiguration` and the event store bean is wired automatically.
- **Flyway-managed schemas** — `outbox_schema.outbox` and `event_sourcing_schema.event_store` are created and migrated by the toolkit itself.

## Requirements

- **Java 21**
- **Maven 3.9+** (or use the included `./mvnw` wrapper)
- **PostgreSQL 14+** with logical replication enabled (`wal_level = logical`)
- **RabbitMQ 3.11+** (for modules that publish messages)
- Spring Boot **3.5.x**

## Installation

Add the GitHub Packages repository and the desired modules to your project.

### Maven `settings.xml`

Authenticate against GitHub Packages (a PAT with `read:packages` scope is required):

```xml
<server>
  <id>github</id>
  <username>YOUR_GITHUB_USERNAME</username>
  <password>YOUR_GITHUB_PAT</password>
</server>
```

### `pom.xml` — repository declaration

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/java-toolkit</url>
  </repository>
</repositories>
```

### Dependency declarations

Use the BOM-style `dependencyManagement` import or declare individual modules:

```xml
<!-- Core domain model — always required -->
<dependency>
  <groupId>io.twba</groupId>
  <artifactId>toolkit-core</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Outbox (JPA + PostgreSQL) -->
<dependency>
  <groupId>io.twba</groupId>
  <artifactId>toolkit-outbox-jpa-postgres</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Event store (JDBC + PostgreSQL) -->
<dependency>
  <groupId>io.twba</groupId>
  <artifactId>toolkit-event-store-jdbc-postgres</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot auto-configuration (wires event store automatically) -->
<dependency>
  <groupId>io.twba</groupId>
  <artifactId>toolkit-autoconfiguration</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### 1. Define a domain event

```java
public class OrderPlaced extends DomainEventPayload {
    private final String orderId;
    private final BigDecimal total;
    // constructor, getters …
}
```

### 2. Define an aggregate

```java
public class Order extends Entity {

    public void place(String orderId, BigDecimal total) {
        // validate …
        record(new OrderPlaced(orderId, total)); // buffers the event
    }
}
```

### 3. Define and register a command handler

```java
@Component
public class PlaceOrderHandler implements CommandHandler<PlaceOrderCommand> {

    private final OrderRepository repository;

    @Override
    @AppendEvents   // triggers DomainEventAppenderConcern AOP aspect
    public void handle(PlaceOrderCommand cmd) {
        Order order = new Order();
        order.place(cmd.orderId(), cmd.total());
        repository.save(order);
    }
}
```

### 4. Dispatch the command

```java
commandBus.dispatch(new PlaceOrderCommand(orderId, total));
// Events are written to the outbox table within the same transaction.
// Debezium captures the outbox insert and publishes to RabbitMQ automatically.
```

### 5. Enable auto-configuration

Add to `application.yaml`:

```yaml
io:
  twba:
    tk:
      properties:
        event-sourcing:
          type: postgres   # activates EventStoreJdbcPostgres bean
```

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│  Your Service                                              │
│                                                            │
│  CommandBus ──► CommandHandler ──► Aggregate.record()      │
│       │                  │                                 │
│       │     (same TX)    ▼                                 │
│       │           OutboxJpa ──► outbox_schema.outbox       │
│       │                              │                     │
│       └──────────────────────────────┘                     │
│                                                            │
│  EventStoreJdbc ◄── retrieveEventsFor() (CQRS read side)  │
└────────────────────────────────────────────────────────────┘
                          │ Debezium CDC (logical replication)
                          ▼
         toolkit-state-propagation-debezium-runtime
                          │
              CloudEvent (AMQP binding)
                          │
                          ▼
                      RabbitMQ
                 Exchange: __MR__<appname>
                 Routing key: event.type
```

**Data flow:**
1. A command is dispatched to `CommandBus`.
2. `TransactionalCommandHandlerDecorator` opens a database transaction.
3. `CommandHandler` mutates the aggregate; `record()` buffers domain events in-memory.
4. `PublishBufferedEventsCommandHandlerDecorator` writes buffered events to the `outbox` table inside the same transaction.
5. Debezium captures the `INSERT` via PostgreSQL logical replication.
6. `CloudEventRecordChangeConsumer` converts the CDC record to a CloudEvent.
7. `MessagePublisherRabbitMq` publishes it to a RabbitMQ topic exchange.

## Configuration

### Auto-configuration properties

| Property | Default | Description |
|---|---|---|
| `io.twba.tk.properties.event-sourcing.type` | _(none)_ | Set to `postgres` to activate `EventStoreJdbcPostgres` bean |

### CDC runtime (`toolkit-state-propagation-debezium-runtime`)

Set via environment variables when running the standalone service:

| Variable | Description |
|---|---|
| `CDC_HOST` | PostgreSQL hostname |
| `CDC_DB_NAME` | Database name |
| `CDC_DB_USER` | Database user |
| `CDC_DB_PASSWORD` | Database password |
| `CDC_OUTBOX_TABLE` | Fully-qualified outbox table (e.g. `outbox_schema.outbox`) |
| `CDC_SERVICE_NAME` | Logical name for this relay instance |

RabbitMQ connection is configured via standard Spring AMQP properties (`spring.rabbitmq.*`).

### Security properties

| Property | Description |
|---|---|
| `io.twba.tk.security.jwt-secret-key` | JWT signing secret |
| `io.twba.tk.security.jwt-ttl` | Token TTL (duration) |

## Database Schemas

The toolkit manages its own schemas via Flyway — no manual DDL required.

| Schema | Table | Managed by |
|---|---|---|
| `outbox_schema` | `outbox` | `toolkit-outbox-jpa-postgres` |
| `event_sourcing_schema` | `event_store` | `toolkit-event-store-jdbc-postgres` |

PostgreSQL must have `wal_level = logical` in `postgresql.conf` for CDC to function.

## Development

### Build from source

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/java-toolkit.git
cd java-toolkit
./mvnw clean verify
```

All integration tests use Testcontainers and require Docker to be running.

### Run only unit tests (no Docker required)

```bash
./mvnw test -pl toolkit-core,toolkit-outbox-jpa-postgres
```

### Run the CDC runtime locally

```bash
export CDC_HOST=localhost
export CDC_DB_NAME=mydb
export CDC_DB_USER=postgres
export CDC_DB_PASSWORD=secret
export CDC_OUTBOX_TABLE=outbox_schema.outbox
export CDC_SERVICE_NAME=my-service

./mvnw -pl toolkit-state-propagation-debezium-runtime spring-boot:run
```

## Publishing

### CI (GitHub Actions)

Every push to `main` or `master` triggers the [publish workflow](.github/workflows/publish.yml). It builds all modules, runs tests, and deploys JARs to GitHub Packages using the built-in `GITHUB_TOKEN` — no additional secrets are required.

### Local publishing to GitHub Packages

1. Create a GitHub Personal Access Token (PAT) with the `write:packages` scope at  
   **GitHub → Settings → Developer settings → Personal access tokens**.

2. Export credentials as environment variables:

   ```bash
   export GITHUB_ACTOR=your-github-username
   export GITHUB_TOKEN=ghp_your_personal_access_token
   ```

3. Update the `distributionManagement` URL in `pom.xml` with your actual GitHub username and repository name:

   ```xml
   <url>https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/java-toolkit</url>
   ```

4. Run the deploy command, pointing Maven at the project settings file:

   ```bash
   ./mvnw clean deploy -DskipTests -s .mvn/settings.xml
   ```

   The `.mvn/settings.xml` file reads `${env.GITHUB_ACTOR}` and `${env.GITHUB_TOKEN}` at runtime — no credentials are stored in the repository.

5. Verify the packages appear at:  
   `https://github.com/YOUR_GITHUB_USERNAME/java-toolkit/packages`

## Contributing

1. Fork the repository and create a feature branch from `main`.
2. Follow the existing module structure — infrastructure concerns belong in dedicated modules, domain abstractions in `toolkit-core`.
3. Add tests; integration tests should use Testcontainers.
4. Open a pull request against `main`. CI must pass before merge.

## License

This project does not currently include a `LICENSE` file. Contact the repository owner for licensing terms before using in production.
