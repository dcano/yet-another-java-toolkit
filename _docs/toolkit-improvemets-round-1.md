# java-toolkit Improvements Proposal

Improvements identified while building the `patient-results-workspace` feature — specifically the outbox → Debezium CDC → RabbitMQ → projector pipeline and its E2E test coverage.

---

## 1. Consumer-side CloudEvents AMQP binding support

**Context:**
The relay correctly publishes in **CloudEvents AMQP binary content mode** per the [CloudEvents AMQP Protocol Binding spec](https://github.com/cloudevents/spec/blob/main/cloudevents/bindings/amqp-protocol-binding.md): event attributes (including `type`) are placed in AMQP application properties (`cloudEvents_type`, `cloudEvents_source`, etc.) and the message body carries only the raw event data payload. This is the right design — it is spec-compliant, efficient, and enables broker-side attribute filtering without body inspection.

**Problem:**
The toolkit provides no consumer-side support for reading these attributes. A downstream projector/consumer must manually annotate with `@Header("cloudEvents_type")` (knowing the exact AMQP property name) or resort to per-listener workarounds. Without guidance, the natural mistake is to look for `type` inside the body JSON, which silently no-ops on every message.

**Proposed improvement:**
Provide a consumer-side abstraction that reads the `cloudEvents_type` AMQP application property transparently. Two options, in order of preference:

**Option A — `@CloudEventListener` meta-annotation:**
```java
@CloudEventListener(eventType = LabResultReceivedEvent.class)
public void onReceived(LabResultReceivedEvent event) {
    // Toolkit extracts cloudEvents_type from header, deserializes body to the declared type
}
```

**Option B — `CloudEventMessageConverter` for Spring AMQP:**
A `MessageConverter` implementation registered on the listener container that reads `cloudEvents_type` from the application property and uses it to drive deserialization of the body, so listeners can declare `@RabbitListener` methods receiving the correct domain event type directly.

Either option encapsulates the header-name knowledge in the toolkit and makes the CloudEvents contract visible in consumer code rather than being an invisible runtime convention.

---

## 2. Publisher confirms (or mandatory flag)

**Problem:**
`MessagePublisherRabbitMq` uses fire-and-forget. If publishing fails (e.g., unroutable message, channel-level error), the failure is silently swallowed. The outbox record is consumed by Debezium but the domain event never reaches consumers, with no signal to the system.

**Proposed improvement:**
Enable `mandatory = true` on the `RabbitTemplate` in `MessagePublisherRabbitMq` so the broker returns unroutable messages via `basic.return`, and register a `ReturnsCallback` that logs or re-queues them. Alternatively, use correlated publisher confirms (`publisher-confirms: CORRELATED`) for full end-to-end delivery acknowledgement.

```java
rabbitTemplate.setMandatory(true);
rabbitTemplate.setReturnsCallback(returned ->
    log.error("Message returned unroutable: exchange={}, routingKey={}, replyText={}",
        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
```

---

## 3. Exchange declaration on the publisher side

**Problem:**
Only consumers (via `@RabbitListener` + `@Exchange`) declare the `__MR__<appname>` topic exchange. If the producing service starts and the CDC relay publishes events before any consumer has connected and declared the exchange, the broker returns a channel-level `NOT_FOUND` error and the messages are lost.

**Proposed improvement:**
`CdcRelayConfig` (or `MessagePublisherRabbitMq`) should declare the exchange before the first publish. Since `RabbitAdmin` is already on the classpath in any Spring AMQP application, this is a one-liner:

```java
@Bean
public Declarables cdcExchangeDeclaration(ApplicationProperties applicationProperties) {
    return new Declarables(
        new TopicExchange("__MR__" + applicationProperties.getName(), true, false));
}
```

This removes the startup race condition and makes the relay self-sufficient regardless of consumer availability.

---

## 4. Dead-letter queue (DLQ) guidance and default configuration

**Problem:**
The toolkit provides no default DLQ setup for projector queues. If a consumer throws during message processing, Spring AMQP NACKs and requeues the message indefinitely, creating a poison-message loop. If the consumer crashes mid-processing, the message may be lost without a DLQ.

**Proposed improvement:**
Document (and optionally provide a factory method for) a standard DLQ pattern for toolkit-managed queues:

```java
@Bean
public Declarables projectorQueuesWithDlq() {
    var dlx = new DirectExchange("dlx.lab-result-projector");
    var dlq = new Queue("dlq.lab-result-projector");
    var binding = BindingBuilder.bind(dlq).to(dlx).with("lab-result-projector");

    var mainQueue = QueueBuilder.durable("lab-result-projector")
        .withArgument("x-dead-letter-exchange", "dlx.lab-result-projector")
        .build();

    return new Declarables(dlx, dlq, binding, mainQueue);
}
```

A `@EnableToolkitDlq` auto-configuration or a `ToolkitQueueBuilder` utility would lower the barrier for consumers to adopt this safely.

---

## 5. Document the exchange naming convention

**Problem:**
The `__MR__<twba.application.name>` convention is non-obvious and not prominently documented. Consumers need to know the exact exchange name to bind to, and the name is derived at runtime from the producer's `twba.application.name` property — which may contain hyphens or other characters. Getting this wrong produces no error at startup; queues simply receive no messages.

**Proposed improvement:**
Add a dedicated section to the toolkit guide listing:
- The exchange name formula: `"__MR__" + twba.application.name`
- The routing key formula: lowercase fully-qualified event class name
- A worked example showing how a consumer service binds to a producer's exchange
- A note that `twba.application.name` must exactly match the value configured in the producing service

---

## Summary

| # | Area | Impact | Breaking change? |
|---|---|---|---|
| 1 | Consumer-side CloudEvents AMQP binding support | High — prevents silent no-ops in all projectors | No (additive) |
| 2 | Publisher confirms / mandatory flag | High — eliminates silent message loss | Potentially (behaviour change) |
| 3 | Exchange declaration on publisher side | Medium — fixes startup race condition | No (additive) |
| 4 | DLQ guidance and defaults | Medium — prevents poison-message loops | No (additive) |
| 5 | Exchange naming convention docs | Low — reduces onboarding friction | No (docs only) |
