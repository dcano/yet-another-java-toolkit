# Spec for domain-query-support

branch: feature/domain-query-support

## Summary

Add first-class support for domain queries following the same structural pattern established for commands. A domain query is a read-only operation dispatched through a typed bus that routes to a registered query handler. The query carries its input parameters; the handler returns a typed result. Queries must not cause side effects (no events recorded, no outbox writes, no transaction needed for reads).

The goal is symmetry: developers using the toolkit should find that modeling a query feels exactly as familiar as modeling a command, with a `DomainQuery<R>`, `QueryHandler<Q, R>`, and `QueryBus` that mirror `DomainCommand`, `CommandHandler`, and `CommandBus`.

## Functional Requirements

- Introduce a `DomainQuery<R>` marker interface (or abstract class) where `R` is the expected return type.
- Introduce a `QueryHandler<Q extends DomainQuery<R>, R>` interface with a single `handle(Q query): R` method.
- Introduce a `QueryBus` interface with a `dispatch(Q query): R` method that routes to the registered `QueryHandler`.
- Provide an in-process synchronous implementation (`QueryBusInProcess`) that discovers all `QueryHandler` beans at startup, analogous to `CommandBusInProcess`.
- Queries should carry a unique query identifier and an issued-at timestamp, analogous to `commandUid` and `occurredAt` on `DefaultDomainCommand`.
- Provide a `DefaultDomainQuery<R>` base implementation with the above fields pre-populated (UUID + `Instant.now()`).
- `QueryBusInProcess` must validate at startup that no two handlers are registered for the same query type; fail fast with a descriptive error.
- No outbox, no transaction decorator, and no event appending are involved in the query path.
- Instrumentation: optionally wrap query handling with a Micrometer timer (`twba.query.execution` tagged with the query name), consistent with the command instrumentation approach. Controlled by the same `io.twba.tk.properties.instrumentation.enabled` flag.
- Auto-configuration: `ToolkitAutoconfiguration` should register `QueryBusInProcess` as a `@ConditionalOnMissingBean(QueryBus.class)` bean so consuming services can override it.
- Multi-tenancy: queries may optionally carry a `TenantId` for tenant-scoped reads; this should follow the same pattern as `MultiTenantEntity`.

## Possible Edge Cases

- No `QueryHandler` registered for a dispatched query type — must throw a clear, descriptive exception (not `NullPointerException`).
- Two `QueryHandler` beans registered for the same query type — must be detected at startup and fail with a meaningful error.
- Handler throws a runtime exception — the exception should propagate to the caller unchanged (no swallowing), as there is no transaction to roll back.
- Query with a null or blank query identifier — should be rejected at construction time.
- `QueryBus` bean already defined by the consuming service — auto-configuration must not override it (`@ConditionalOnMissingBean`).
- Instrumentation disabled — timer decoration must be skipped entirely without affecting correctness.

## Acceptance Criteria

- A concrete query and handler can be defined in a consuming service with the same effort as a command/handler pair.
- Dispatching via `queryBus.dispatch(query)` returns the typed result from the handler.
- Attempting to dispatch a query with no registered handler throws a descriptive exception.
- Registering two handlers for the same query type fails at Spring context startup, not at dispatch time.
- The `twba.query.execution` Micrometer timer is recorded when instrumentation is enabled.
- `QueryBusInProcess` is auto-configured and can be replaced by a custom `QueryBus` bean in the consuming service.
- Unit tests cover: successful dispatch, missing-handler error, duplicate-handler startup error, instrumentation on/off.

## Open Questions

- Should `DomainQuery<R>` be an interface or an abstract class? Using an abstract class (like `DefaultDomainCommand`) would allow pre-populating `queryUid` and `issuedAt` automatically; an interface keeps it lighter but requires the consumer to provide a base implementation. - Create DomainQuery interface and provide a DefailDomainQuery class.
- Should the query bus support async/reactive dispatch in a future extension, and does the current interface shape allow for it without breaking changes? - Use Flux (reactive) for query bus.
- Should multi-tenancy be enforced at the bus level (require a `TenantId` on every query) or remain opt-in (only when the query implements `TenantAware`)? - Only when query implements TenantAware.
- Is a decorator pattern needed for the query bus (analogous to the command decorator chain), or is direct delegation to the handler sufficient for the foreseeable use cases? - Yes, it is needed. Add a metrics (instrumentation) decorator.

## Testing Guidelines

Create a test file(s) in the appropriate `src/test` folder for the new module. Cover the following cases without going too heavy:

- Successful dispatch of a query returns the correct typed result from the handler.
- Dispatching a query type with no registered handler throws a descriptive exception.
- Registering duplicate handlers for the same query type is detected at context startup.
- Instrumentation timer is recorded when enabled, and not recorded when disabled.
- A custom `QueryBus` bean defined by the consumer suppresses the auto-configured one.
