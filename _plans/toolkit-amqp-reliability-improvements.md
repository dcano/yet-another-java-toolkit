# Implementation Plan: Toolkit AMQP Reliability Improvements

Spec: `_specs/toolkit-amqp-reliability-improvements.md`
Branch: `feature/toolkit-amqp-reliability-improvements`

---

## Context

The current state of the relevant code:

- `MessagePublisherRabbitMq` — fire-and-forget; no mandatory flag, no returns callback, no exchange declaration.
- `RabbitMqTopologyConfiguration` — declares the topic exchange but is annotated `@Profile("dev")`, so it never runs in non-dev environments. A TODO in the file confirms this is a known gap.
- `CloudEventRecordChangeConsumer` — uses `cdcRecord.valueOf("type")` as the CloudEvent `type`, which is the fully-qualified Java class name stored in the outbox `type` column.
- `toolkit-message-publisher-rabbitmq` — has no test dependencies declared and no existing tests.
- `TwbaCloudEvent.CLOUD_EVENT_AMQP_BINDING_PREFIX` = `"cloudEvents_"` — this is the AMQP header prefix used by the publisher; consumers must know `"cloudEvents_type"` to read the event type.

---

## Implementation Steps

### Step 1 — Publisher mandatory flag and returns callback (Item 2)

**File:** `toolkit-message-publisher-rabbitmq/src/main/java/io/twba/tk/cdc/MessagePublisherRabbitMq.java`

- Remove the two `//TODO` comments for ACK and DLQ.
- Add a `boolean returnsEnabled` flag to the constructor (or accept it via a separate factory/builder).
- Call `rabbitTemplate.setMandatory(true)` and register a `ReturnsCallback` that logs exchange, routing key, and reply text at `ERROR` level when `returnsEnabled` is `true`.
- The `publish()` method signature and return type remain unchanged.

**File:** `toolkit-state-propagation-debezium-runtime/src/main/java/io/twba/tk/cdc/message_relay/config/DebeziumConfiguration.java`

- Add a boolean property binding (e.g. `cdc.publisher-returns`, default `false`) read from `MessageRelayProps`.
- Pass that flag when constructing `MessagePublisherRabbitMq`.
- Also set `rabbitTemplate.setPublisherReturns(true)` on the `RabbitTemplate` bean when the flag is `true` (required for `ReturnsCallback` to fire).

**File:** `toolkit-cdc-debezium-postgres/src/main/java/io/twba/tk/cdc/MessageRelayProps.java` (or create a new `PublisherRabbitMqProps`)

- Add `boolean publisherReturns` field, default `false`.

---

### Step 2 — Exchange self-declaration on the publisher side (Item 3)

**File:** `toolkit-state-propagation-debezium-runtime/src/main/java/io/twba/tk/cdc/message_relay/config/RabbitMqTopologyConfiguration.java`

- Remove the `@Profile("dev")` annotation entirely.
- The `TopicExchange` bean already uses `"__MR__" + messageRelayProps.getServiceName()` — keep that formula unchanged.
- The `TopicExchange` must be declared durable and non-auto-delete (Spring AMQP defaults when using the single-arg constructor `new TopicExchange(name)` are already durable=true, autoDelete=false — verify this and make it explicit with the three-arg constructor `new TopicExchange(name, true, false)`).
- `RabbitAdmin` is provided automatically by `spring-boot-starter-amqp`; the `TopicExchange` bean will be picked up and declared on connection.

---

### Step 3 — `CloudEventMessageConverter` (Item 1, explicit opt-in)

**New file:** `toolkit-message-publisher-rabbitmq/src/main/java/io/twba/tk/cdc/CloudEventMessageConverter.java`

- Implement `org.springframework.amqp.support.converter.MessageConverter`.
- Constructor accepts a `tools.jackson.databind.ObjectMapper` (Jackson 3, as used in the rest of the project).
- `fromMessage(Message message)`:
  1. Read header `"cloudEvents_type"` from `message.getMessageProperties().getHeaders()`.
  2. If the header is absent or blank: log a warning and throw `MessageConversionException` (never swallow silently).
  3. Resolve the target class via `Class.forName(cloudEventType)` — wrap `ClassNotFoundException` in `MessageConversionException`.
  4. Deserialize `message.getBody()` to the resolved class using the injected `ObjectMapper`.
- `toMessage(Object object, MessageProperties props)`: throw `UnsupportedOperationException` — this converter is consumer-side only.
- No `@Component`, no `@Bean`, no auto-configuration. Consumers instantiate and wire it explicitly.

**No changes to any auto-configuration or `spring.factories`/`AutoConfiguration.imports`.**

---

### Step 4 — `ToolkitQueueBuilder` DLQ utility (Item 4)

**New file:** `toolkit-message-publisher-rabbitmq/src/main/java/io/twba/tk/cdc/ToolkitQueueBuilder.java`

- A plain utility class (no Spring annotations) with a static factory method:
  `ToolkitQueueBuilder.forQueue(String queueName)` — returns a builder.
- Builder exposes `.withDeadLetterExchange(String dlxName)` (optional, defaults to `"dlx." + queueName`).
- Builder exposes `.build()` returning a `org.springframework.amqp.core.Declarables` containing:
  - A `DirectExchange` for the DLX.
  - A `Queue` for the DLQ (named `"dlq." + queueName`).
  - A `Binding` from the DLQ to the DLX with the routing key equal to the queue name.
  - The main `Queue` declared durable with `x-dead-letter-exchange` argument pointing at the DLX.
- No auto-configuration. Consuming services declare a `@Bean Declarables` using this builder.

---

### Step 5 — Test dependencies and integration tests

**File:** `toolkit-message-publisher-rabbitmq/pom.xml`

Add to `<dependencies>` (all `<scope>test</scope>`):
- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.boot:spring-boot-starter-amqp-test`
- `org.testcontainers:testcontainers-junit-jupiter`
- `org.testcontainers:rabbitmq`
- `org.awaitility:awaitility`
- `org.assertj:assertj-core`
- `ch.qos.logback:logback-classic`

**New test file:** `toolkit-message-publisher-rabbitmq/src/test/java/io/twba/tk/cdc/CloudEventMessageConverterTest.java`

Tests (unit, no container needed):
- `fromMessage` with a valid `cloudEvents_type` header deserializes the body to the correct type.
- `fromMessage` with a missing `cloudEvents_type` header throws `MessageConversionException`.
- `fromMessage` with an unknown class name throws `MessageConversionException`.
- `toMessage` throws `UnsupportedOperationException`.

**New test file:** `toolkit-message-publisher-rabbitmq/src/test/java/io/twba/tk/cdc/MessagePublisherRabbitMqIT.java`

Integration tests using `@Testcontainers` with a RabbitMQ container:
- Publishing to a routable exchange+queue delivers the message (happy path).
- Publishing with `publisher-returns=true` to an exchange with no bound queue triggers the `ReturnsCallback` and logs the error (verify via a `ListAppender` or mock logger).
- Exchange declared by `ToolkitQueueBuilder`'s `Declarables` exists on the broker after context startup.
- NACK'd message on a queue created with `ToolkitQueueBuilder` is routed to the DLQ (publish a message, NACK it, verify it appears on `dlq.*`).

**Note:** The exchange self-declaration fix in `RabbitMqTopologyConfiguration` is tested by the existing approach in `toolkit-state-propagation-debezium-runtime` — no new integration test needed there, since removing `@Profile("dev")` is a config-only change.

---

### Step 6 — Documentation (Item 5)

**File:** `_skills/toolkit-guide.md` (the toolkit-guide skill file)

Add a new section titled **AMQP Consumer Wiring** covering:
- Exchange name formula: `"__MR__" + twba.application.name` of the **producing** service.
- Routing key formula: lowercase fully-qualified Java class name of the domain event (stored as the CloudEvent `type`).
- How to register `CloudEventMessageConverter` on a `SimpleRabbitListenerContainerFactory`.
- A worked example showing a `@RabbitListener` bound to the correct exchange, queue, and routing key.
- A warning box: if `twba.application.name` in the consumer does not exactly match the producer's value, the queue receives no messages and no error is raised at startup.
- How to use `ToolkitQueueBuilder` to declare the consumer queue with DLQ.

---

## File Change Summary

| File | Change type | Item |
|---|---|---|
| `toolkit-message-publisher-rabbitmq/.../MessagePublisherRabbitMq.java` | Modify | 2 |
| `toolkit-state-propagation-debezium-runtime/.../DebeziumConfiguration.java` | Modify | 2 |
| `toolkit-cdc-debezium-postgres/.../MessageRelayProps.java` | Modify | 2 |
| `toolkit-state-propagation-debezium-runtime/.../RabbitMqTopologyConfiguration.java` | Modify | 3 |
| `toolkit-message-publisher-rabbitmq/.../CloudEventMessageConverter.java` | New | 1 |
| `toolkit-message-publisher-rabbitmq/.../ToolkitQueueBuilder.java` | New | 4 |
| `toolkit-message-publisher-rabbitmq/pom.xml` | Modify | 1,2,4 |
| `toolkit-message-publisher-rabbitmq/.../CloudEventMessageConverterTest.java` | New | 1 |
| `toolkit-message-publisher-rabbitmq/.../MessagePublisherRabbitMqIT.java` | New | 2,3,4 |
| `_skills/toolkit-guide.md` | Modify | 5 |

---

## Implementation Order

1. Step 2 (exchange declaration) — smallest, self-contained, unblocks manual testing immediately.
2. Step 1 (mandatory flag) — modifies existing `MessagePublisherRabbitMq`; straightforward.
3. Step 3 (`CloudEventMessageConverter`) — new class, no dependencies on Steps 1–2.
4. Step 4 (`ToolkitQueueBuilder`) — new class, no dependencies on Steps 1–3.
5. Step 5 (tests + pom deps) — covers all new behaviour introduced in Steps 1–4.
6. Step 6 (documentation) — last, once implementation is verified.
