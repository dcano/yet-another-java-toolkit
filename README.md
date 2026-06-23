# yet-another-java-toolkit

A Spring Boot multi-module toolkit for building event-driven microservices with event sourcing, CQRS, and CDC-based state propagation over PostgreSQL and RabbitMQ.

[![Build and Publish](https://github.com/dcano/yet-another-java-toolkit/actions/workflows/publish.yml/badge.svg)](https://github.com/dcano/java-toolkit/actions/workflows/publish.yml)

## Overview

java-toolkit provides a coherent set of infrastructure libraries for the `io.twba` ecosystem. It implements the **Outbox Pattern** for transactional event publishing, **Event Sourcing** with a JDBC-backed event store, **CQRS** command dispatching with decorator-based pipelines, and **Change Data Capture** via an embedded Debezium engine. The toolkit is designed for Java 25 and Spring Boot 4.0, and targets PostgreSQL as the persistence layer with RabbitMQ as the message broker.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `toolkit-core` | `io.twba:toolkit-core` | Domain model abstractions, command bus, event buffering, outbox/CDC interfaces |
| `toolkit-outbox-jpa-postgres` | `io.twba:toolkit-outbox-jpa-postgres` | JPA/Hibernate outbox table implementation with Flyway migrations |
| `toolkit-event-store-jdbc-postgres` | `io.twba:toolkit-event-store-jdbc-postgres` | JDBC event store for PostgreSQL |
| `toolkit-cdc-debezium-postgres` | `io.twba:toolkit-cdc-debezium-postgres` | Embedded Debezium CDC relay from outbox table |
| `toolkit-message-publisher-rabbitmq` | `io.twba:toolkit-message-publisher-rabbitmq` | RabbitMQ CloudEvents publisher + consumer-side wiring (`EventRegistry`, `CloudEventMessageConverter`, `ToolkitQueueBuilder`) |
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

- **Java 25**
- **Maven 3.9+** (or use the included `./mvnw` wrapper)
- **PostgreSQL 14+** with logical replication enabled (`wal_level = logical`)
- **RabbitMQ 3.11+** (for modules that publish messages)
- Spring Boot **4.0.x**

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

## Consuming Events from RabbitMQ

A producing service publishes each domain event to a topic exchange named
`__MR__<producer twba.application.name>`, using the **lowercased fully-qualified class
name** of the event as the routing key (and as the CloudEvent `type`, carried in the
`cloudEvents_type` AMQP header). A consuming service subscribes by binding a queue to that
exchange and routing key.

> ⚠️ **Silent failure.** If the exchange name or routing key you bind to does not *exactly*
> match the producer's, RabbitMQ still creates the binding, no error is raised at startup,
> and your queue simply receives nothing. Verify the producer's `twba.application.name` and
> the event's fully-qualified class name (lowercased).

### 1. Declare your events with `@EventDefinition`

Because the wire `type` is lowercased, the consumer cannot reconstruct the class name by
case. Annotate every event class you want to consume with `@EventDefinition` so the toolkit
can index it. The class must be deserializable by Jackson (a matching shape is enough — it
does not need to be the producer's exact class).

```java
import io.twba.tk.event.EventDefinition;

@EventDefinition
public record OrderPlaced(String orderId, BigDecimal total) { }
```

By default the registry maps the event by the lowercased fully-qualified class name — the
same value the producer emits. Override it with `@EventDefinition(type = "...")` only when
the producer publishes a non-default custom type.

### 2. Expose an `EventRegistry` over your events' base package

`EventRegistryReflection` scans the given package at startup and indexes every
`@EventDefinition` class. The base package is configurable.

```java
@Bean
EventRegistry eventRegistry() {
    return new EventRegistryReflection("com.acme.orders.events");
}
```

### 3. Register `CloudEventMessageConverter` on a listener container factory

The converter reads the `cloudEvents_type` header, resolves the class via the registry, and
deserializes the body — so your listener method receives a typed event, not raw JSON. It is
**explicit opt-in**: existing `@RabbitListener` methods that don't use this factory keep
working unchanged.

```java
@Bean
SimpleRabbitListenerContainerFactory cloudEventListenerContainerFactory(
        ConnectionFactory connectionFactory,
        EventRegistry eventRegistry,
        JsonMapper objectMapper) {                       // tools.jackson.databind.json.JsonMapper
    var factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(new CloudEventMessageConverter(objectMapper, eventRegistry));
    return factory;
}
```

### 4. Declare the consumer queue with a dead-letter queue

`ToolkitQueueBuilder` returns a `Declarables` with a durable main queue wired to a
dead-letter exchange and queue, so rejected/NACK'd messages are dead-lettered to
`dlq.<queue>` instead of looping forever. `RabbitAdmin` (from `spring-boot-starter-amqp`)
declares it on connection.

```java
@Bean
Declarables ordersQueueTopology() {
    return ToolkitQueueBuilder.forQueue("orders-projector.order-events").build();
    // creates: exchange dlx.<queue>, queue dlq.<queue>, the binding, and the main queue
    // with x-dead-letter-exchange + x-dead-letter-routing-key set.
}
```

### 5. Listen with a typed method

Bind the listener to the producer's exchange and routing key, and reference the converter
factory. Use the same queue name declared above (drop the inline `@Queue` declaration to
avoid declaring it twice).

```java
@Component
class OrderProjector {

    @RabbitListener(
        containerFactory = "cloudEventListenerContainerFactory",
        bindings = @QueueBinding(
            value    = @Queue(value = "orders-projector.order-events", durable = "true"),
            exchange = @Exchange(value = "__MR__order-management", type = "topic"),
            key      = "com.acme.orders.events.orderplaced"   // lowercased FQN of the event
        ))
    public void on(OrderPlaced event) {
        // fully deserialized — no header parsing, no Class.forName
    }
}
```

A message whose `cloudEvents_type` is missing or not registered raises a
`MessageConversionException` (logged, never silently swallowed).

### Publisher delivery guarantees (producer side)

The CDC relay can be told to flag unroutable messages instead of dropping them silently.
Set `cdc.publisher-returns: true` on the relay: it enables RabbitMQ's mandatory flag and a
returns callback that logs the exchange, routing key, and broker reply text at `ERROR` when
a message cannot be routed to any queue. Defaults to `false`.

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

### Metrics

The toolkit publishes Micrometer metrics (exported through whatever registry your service
configures, e.g. Prometheus via Spring Boot Actuator).

| Metric | Type | Where | Enabled by | Description |
|---|---|---|---|---|
| `twba.command.execution` | Timer | Producing service | `io.twba.tk.properties.instrumentation.enabled=true` | Command execution time, tagged `command`. |
| `twba.query.execution` | Timer | Producing service | `io.twba.tk.properties.instrumentation.enabled=true` | Query execution time, tagged `query`. |
| `twba.outbox.messages.published` | Counter | Producing service (`OutboxJpa`) | `instrumentation.enabled=true` **and** a `MeterRegistry` bean | One increment per message written to the outbox table. |
| `twba.outbox.messages.read` | Counter | CDC relay (`CloudEventRecordChangeConsumer`) | a `MeterRegistry` bean (present via actuator) | One increment per outbox row read and relayed by Debezium. |

**Generated vs consumed ratio.** The `published` and `read` counters bracket the
outbox → CDC pipeline. As the producing service and the CDC relay are usually separate
processes, compute the ratio in your metrics backend — e.g. in PromQL:

```promql
rate(twba_outbox_messages_read_total[5m]) / rate(twba_outbox_messages_published_total[5m])
```

A value near `1` means the relay keeps pace; a sustained value below `1` means events are
piling up in the outbox faster than they are relayed downstream.

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

## Changelog

### v2.1.0 — 2026-06-21

#### Fixed
- Delete outbox rows from the database after successful relay to prevent unbounded table growth
- Use durable JDBC-backed Debezium offset storage so restarts do not replay already-relayed events

#### Added
- Percentile publication for `twba.query.execution` timer metrics

---

### v2.0.0 — 2026-06-14

#### Added
- AMQP reliability: publisher returns callback (`cdc.publisher-returns`) logs unroutable messages instead of dropping them silently
- `ToolkitQueueBuilder` — creates a durable main queue wired to a dead-letter exchange/queue (`dlx.<queue>` / `dlq.<queue>`)
- `EventRegistry` / `@EventDefinition` + `CloudEventMessageConverter` for typed consumer-side event deserialization
- Percentile publication for `twba.command.execution` timer metrics

---

### v1.0.0 — 2026-05-23

#### Breaking Changes
- Migrated runtime to Spring Boot 4.0.6 and Jackson 3 — consuming services must upgrade their Spring Boot version and Jackson dependencies accordingly

---

### v0.0.3 — 2026-05-09

#### Added
- Domain query support: `QueryBus`, `QueryHandler`, and `twba.query.execution` Micrometer timer

---

### v0.0.2 — 2026-05-08

#### Added
- Command execution instrumentation: `twba.command.execution` Micrometer timer, enabled via `io.twba.tk.properties.instrumentation.enabled=true`

---

### v0.0.1 — 2026-05-02

#### Added
- Initial release: outbox pattern (JPA/PostgreSQL), JDBC event store, CDC via embedded Debezium, CQRS command bus with decorator pipeline, Spring Boot auto-configuration, Flyway-managed schemas
