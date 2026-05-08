# Spec for command-handler-execution-time

branch: feature/command-handler-execution-time
figma_component (if used): N/A

## Summary

Add a new decorator to the `CommandBus` decorator chain that measures and records the elapsed time for each command handler execution. The decorator should integrate transparently into the existing `CommandBusInProcess` pipeline without requiring changes to existing handlers or aggregates.

## Functional Requirements

- A new `CommandHandlerDecorator` implementation must wrap each command handler invocation and measure wall-clock execution time.
- The measured duration must be made observable — at minimum logged at a configurable level (e.g. INFO or DEBUG), including the command type and duration in milliseconds.
- The decorator must be applied for every command dispatched through `CommandBusInProcess`, regardless of the handler type.
- The timing measurement must cover the full execution of the delegated handler (including any nested decorators further down the chain), not just the outermost dispatch call.
- The decorator must not alter the outcome of the handler: it must rethrow any exception thrown by the delegate without swallowing or wrapping it.
- The decorator is always present in the pipeline. Its observable output (logging and metrics) is controlled by the `io.twba.tk.properties.instrumentation.enabled` property.

## Figma Design Reference (only if referenced)

N/A

## Possible Edge Cases

- Commands that throw exceptions: timing must still be captured and logged before the exception propagates.
- Commands with very short execution times (sub-millisecond): ensure the time unit used does not lose precision.
- Concurrent command dispatches: each measurement must be scoped to its own invocation and must not interfere with others.
- Decorator ordering: the timing decorator's position in the chain affects what is measured — its placement relative to the transaction decorator must be deliberate and documented.

## Acceptance Criteria

- When a command is dispatched, the execution time of its handler is recorded and emitted (logged) with the command class name and duration.
- If the handler throws, the exception is re-thrown unchanged and the timing is still logged.
- When `instrumentation.enabled=false` (the default), no metrics or log output are produced, but the decorator remains in the pipeline.
- Existing tests continue to pass with the decorator present in the chain.
- The decorator does not introduce any mandatory runtime dependency beyond what is already in `toolkit-core`.

## Open Questions

- Should the timing output target a structured log, a Micrometer metric, or both? A plain log is the minimum; metric emission could be added later. Micrometer metric including command name as label. Log only at debug level and if a configuration property (e.g. instrumentation.enabled) is set.
- Where in the decorator chain should this decorator sit — before or after the transaction decorator? Measuring outside the transaction captures total time including commit; measuring inside captures only handler logic. Outside.
- Should there be a per-command-type threshold above which a warning is emitted instead of an info/debug log? No

## Testing Guidelines

Create a test file(s) in the ./tests folder for the new feature, and create meaningful tests for the following cases, without going too heavy:

- Verifies that after a successful dispatch the measured duration is greater than zero.
- Verifies that when the handler throws, the exception propagates unchanged and timing is still captured.
- Verifies that when `instrumentation.enabled=false`, no metrics or log output are produced even though the decorator is in the chain.
- Verifies that concurrent dispatches do not interfere with each other's timing measurements.
