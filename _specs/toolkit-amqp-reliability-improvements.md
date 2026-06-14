# Spec for toolkit-amqp-reliability-improvements

branch: feature/toolkit-amqp-reliability-improvements

## Summary

A set of targeted improvements to the toolkit's AMQP messaging layer addressing reliability gaps and usability friction identified while building the outbox → Debezium CDC → RabbitMQ → projector pipeline. The changes cover: consumer-side CloudEvents AMQP binding support, publisher delivery guarantees, exchange self-declaration on the publisher side, dead-letter queue defaults for consumer queues, and documentation of the exchange naming convention.

## Functional Requirements

- **Consumer-side CloudEvents support:** Provide a `CloudEventMessageConverter` that transparently reads the `cloudEvents_type` AMQP application property and drives deserialization of the message body to the correct domain event type. The converter must **not** be registered via auto-configuration; consuming services must explicitly wire it into their listener container factory. Existing `@RabbitListener` methods that do not reference the converter must continue to work without any changes (backward compatible, explicit opt-in).
- **Publisher delivery guarantees:** Enable the mandatory flag on `RabbitTemplate` inside `MessagePublisherRabbitMq` so unroutable messages are returned by the broker rather than silently dropped. Register a returns callback that logs the returned message details. Optionally support correlated publisher confirms as a higher-assurance alternative.
- **Exchange self-declaration on the publisher side:** `CdcRelayConfig` (or `MessagePublisherRabbitMq`) must declare the `__MR__<appname>` topic exchange at startup using `RabbitAdmin`, so the relay is not dependent on a consumer having connected first.
- **Dead-letter queue defaults:** Provide a utility or auto-configuration that makes it easy for consuming services to declare a main queue bound to a dead-letter exchange, preventing poison-message requeue loops and message loss on consumer crashes.
- **Exchange naming convention documentation:** Add a dedicated section to the toolkit guide explaining the `__MR__<twba.application.name>` exchange name formula, the routing key formula, a worked consumer-binding example, and the requirement that `twba.application.name` must exactly match the producing service's configured value.

## Possible Edge Cases

- The mandatory flag triggers a `ReturnsCallback` only when `publisher-returns` is enabled in the connection factory; misconfiguration may silently ignore unroutable returns.
- Exchange re-declaration at startup must use durable, non-auto-delete settings matching any existing broker exchange; a type mismatch causes a channel-level error.
- The consumer-side abstraction must handle messages that do not carry a `cloudEvents_type` property gracefully (log and skip, or route to a dead-letter).
- The DLQ utility must not conflict with queues declared via `@RabbitListener` annotations if the consumer service uses both approaches.
- A consumer binding to the wrong exchange name (e.g. wrong `twba.application.name` value) produces no startup error and no messages — the failure is entirely silent; documentation must emphasise this risk prominently.

## Acceptance Criteria

- A projector can consume CloudEvents-formatted AMQP messages by explicitly registering `CloudEventMessageConverter` on its listener container factory and declaring a listener method typed to the domain event class, without referencing the raw `cloudEvents_type` header name anywhere in application code.
- Existing `@RabbitListener` methods in services that do not register `CloudEventMessageConverter` continue to work unchanged.
- Publishing a message to a non-existent exchange or queue causes a loggable error rather than a silent no-op; the error includes exchange name, routing key, and broker reply text.
- Starting the CDC relay before any consumer has connected does not result in `NOT_FOUND` channel errors; the exchange exists on the broker as soon as the relay starts.
- A consuming service can declare a main queue with a dead-letter exchange in one step using a toolkit-provided utility, and confirmed NACK'd messages are routed to the DLQ rather than requeued indefinitely.
- The toolkit guide contains a dedicated section documenting the exchange naming formula, routing key formula, and a worked consumer-binding example.
- All new behaviour is covered by integration tests using Testcontainers (RabbitMQ container).

## Open Questions

- Should publisher confirms be opt-in via configuration property, or always enabled? Enabling CORRELATED confirms changes the threading model and may impact throughput. - Configurable.
- For the consumer-side abstraction, should the toolkit provide Option A (`@CloudEventListener` meta-annotation) or Option B (`CloudEventMessageConverter`)? Option A is more explicit; Option B requires less change to existing `@RabbitListener` methods. **Use Option B** — explicit opt-in only, no auto-configuration, backward compatible.
- Should the DLQ utility be an auto-configuration (zero boilerplate) or a builder class (explicit opt-in)? Auto-configuration risks declaring unexpected queues in services that manage their own topology. - builder
- Should the exchange naming convention be documented in the existing toolkit guide or in a new dedicated consumer onboarding guide? Existing toolkit guide / skill. 

## Testing Guidelines

Create test file(s) in the relevant module `src/test` directories for the new features, and create meaningful tests for the following cases, without going too heavy:

- A listener using the new consumer-side abstraction correctly receives and deserializes a CloudEvents-formatted AMQP message without referencing the raw header name.
- A listener using the new consumer-side abstraction receives a message missing the `cloudEvents_type` property and handles it gracefully (no exception propagates).
- Publishing to an unroutable destination triggers the returns callback and logs the appropriate error details.
- The CDC relay declares the topic exchange on startup; verifying the exchange exists on the broker before any consumer connects.
- A queue declared via the DLQ utility correctly routes NACK'd messages to the dead-letter queue instead of requeuing them.
