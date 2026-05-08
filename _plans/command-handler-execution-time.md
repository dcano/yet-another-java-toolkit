# Implementation Plan: Command Handler Execution Time Decorator

Spec: `_specs/command-handler-execution-time.md`
Branch: `feature/command-handler-execution-time`

---

## Overview

Add an `InstrumentationCommandHandlerDecorator` that is **always** present at the outermost
position of the `CommandBusInProcess` decorator chain. The decorator measures wall-clock execution
time per command dispatch. Metric recording (Micrometer `Timer`) and DEBUG logging are both gated
on `io.twba.tk.properties.instrumentation.enabled=true`; when the property is false (the default)
the decorator is a no-op pass-through.

---

## Current decorator chain (unchanged path)

```
TransactionalCommandHandlerDecorator
  └── PublishBufferedEventsCommandHandlerDecorator
        └── <user handler>
```

## Target decorator chain (always applied)

```
InstrumentationCommandHandlerDecorator          ← NEW (outermost)
  └── TransactionalCommandHandlerDecorator
        └── PublishBufferedEventsCommandHandlerDecorator
              └── <user handler>
```

Placing the new decorator outside the transaction means the measurement covers the full
end-to-end cost: transaction open + handler logic + outbox publish + transaction commit/rollback.

---

## Module impact

| Module                      | Nature of change                                                          |
|-----------------------------|---------------------------------------------------------------------------|
| `toolkit-core`              | New decorator class, new `Instrumentation` property group, optional Micrometer dep |
| `toolkit-autoconfiguration` | New conditional `@Bean` that wires `CommandBusInProcess` with `MeterRegistry`  |

No new Maven module is required.

---

## Task 1 — Add Micrometer as an optional dependency to `toolkit-core`

File: `toolkit-core/pom.xml`

Add `io.micrometer:micrometer-core` with `<optional>true</optional>`. This lets the decorator
reference `MeterRegistry` at compile time without forcing consumers to include Micrometer on the
runtime classpath.

---

## Task 2 — Extend `ToolkitProperties` with an `Instrumentation` sub-section

File: `toolkit-core/src/main/java/io/twba/tk/configure/ToolkitProperties.java`

Add an inner `@Data` class `Instrumentation` with a single `boolean enabled` field and wire it
into `ToolkitProperties`. The resulting configuration key is:

```
io.twba.tk.properties.instrumentation.enabled=true
```

---

## Task 3 — Create `InstrumentationCommandHandlerDecorator`

File: `toolkit-core/src/main/java/io/twba/tk/command/decorator/InstrumentationCommandHandlerDecorator.java`

Constructor parameters:
- `CommandHandler<DomainCommand> delegate`
- `@Nullable MeterRegistry meterRegistry` — when `null` the metric step is skipped
- `boolean enabled` — derived from `properties.getInstrumentation().isEnabled()`; when `false`
  both metric recording and DEBUG logging are skipped and the decorator is a transparent pass-through

Behaviour of `handle(DomainCommand command)`:
1. If `!enabled`: call `delegate.handle(command)` and return immediately (no timing overhead).
2. Otherwise: capture `start = System.nanoTime()`.
3. Call `delegate.handle(command)` inside a `try/finally`.
4. In `finally`: compute `elapsedNanos = System.nanoTime() - start`.
5. If `meterRegistry != null`: record to a `Timer` named `twba.command.execution` with tag
   `command=<command.getClass().getSimpleName()>`.
6. Log at DEBUG — command class name and elapsed milliseconds.
7. Any exception thrown by the delegate propagates unchanged (the `finally` block must not
   swallow it).

---

## Task 4 — Update `CommandBusInProcess` to accept instrumentation parameters

File: `toolkit-core/src/main/java/io/twba/tk/command/CommandBusInProcess.java`

Add a new constructor that accepts `ToolkitProperties` and a nullable `MeterRegistry` alongside
the existing parameters. Update the private `decorate()` method to **always** wrap the existing
chain with `InstrumentationCommandHandlerDecorator`, passing `properties.getInstrumentation().isEnabled()`
and the `MeterRegistry` to the decorator constructor.

The two existing constructors must remain unchanged. To keep the decorator always present in the
chain they should delegate to the new constructor passing a default `ToolkitProperties` with
`instrumentation.enabled=false` and a `null` MeterRegistry — making them transparent pass-throughs.

---

## Task 5 — Wire in `ToolkitAutoconfiguration`

File: `toolkit-autoconfiguration/src/main/java/io/twba/tk/autoconfigure/ToolkitAutoconfiguration.java`

Add a new `@Bean CommandBusInProcess` method annotated with:
- `@ConditionalOnMissingBean(CommandBus.class)` — respect user-defined bus overrides
- `@ConditionalOnBean(TwbaTransactionManager.class)` — only register when transaction support is present

Inject `MeterRegistry` as `@Autowired(required = false)` so the bean is still created when
Micrometer is absent from the classpath. Pass the `MeterRegistry` (possibly `null`) and the
`ToolkitProperties` to the new `CommandBusInProcess` constructor.

---

## Task 6 — Tests

### Unit test: `InstrumentationCommandHandlerDecoratorTest` (new file in `toolkit-core`)

| Case | What to assert |
|------|----------------|
| Successful dispatch | Elapsed time recorded in `SimpleMeterRegistry` under `twba.command.execution`; timer count == 1; value > 0 |
| Handler throws | Exception type and message are unchanged; timer is still recorded (count == 1) |
| `MeterRegistry` is `null` | No `NullPointerException`; handler executes normally |
| `enabled=false` | No metrics recorded, no SLF4J output; handler still executes normally |
| Concurrent dispatches | Two threads each dispatch a different command; their `Timer` records are independent and both > 0 |

### Integration / regression: `CommandBusInProcessTest` (existing file)

- Existing `shouldExecuteCommands` test must still pass with no changes (existing constructors
  now delegate to the new one with `enabled=false`, so the decorator is a no-op).
- Add one case that constructs `CommandBusInProcess` with `instrumentation.enabled=true` and a
  `SimpleMeterRegistry`, dispatches a command, and asserts a timer entry exists.

---

## Out of scope

- Per-command warning thresholds.
- Structured log fields beyond command name and duration.
- Histogram percentile configuration (can be layered on later via standard Micrometer config).
