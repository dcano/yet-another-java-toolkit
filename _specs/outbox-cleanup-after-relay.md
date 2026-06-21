# Spec for outbox-cleanup-after-relay

branch: feature/outbox-cleanup-after-relay

## Summary

After Debezium reads an outbox row and the CDC relay successfully publishes the corresponding message to RabbitMQ, the row is never removed from `outbox_schema.outbox`. This causes two problems: the table grows unboundedly, and any time the Debezium offset is lost (e.g. after a container restart without a persistent volume), Debezium performs an initial snapshot of the full outbox table and republishes every historical event — causing downstream consumers to reprocess events they have already handled.

This feature implements the missing cleanup step so that outbox rows are deleted after successful relay, and validates that the Debezium connector is configured to avoid re-snapshotting existing rows on restart.

## Functional Requirements

- After `CloudEventRecordChangeConsumer` successfully calls `messagePublisher.publish(event)`, the corresponding outbox row must be deleted from `outbox_schema.outbox` by its `uuid`.
- The deletion must happen only on successful publish; a publish failure must not delete the row (to allow retries via Debezium replay).
- `CloudEventRecordChangeConsumer` must receive a collaborator capable of deleting outbox rows by uuid (e.g. an `OutboxCleaner` or equivalent port), injected at construction time.
- The `OutboxJpa` implementation (or a new dedicated adapter) must provide the delete-by-uuid capability without exposing unrelated persistence concerns to the CDC module.
- The Debezium connector configuration must include `snapshot.mode=no_data` (or its equivalent for the target Debezium version) so that on offset loss the connector does not replay the full table before switching to streaming mode.
- The `snapshot.include.collection.list` property in `DebeziumConfigurationProvider` should be removed or reconsidered given the `snapshot.mode=no_data` setting, since it has no effect when snapshotting is disabled.

## Possible Edge Cases

- `messagePublisher.publish(event)` throws after partial delivery: row must not be deleted.
- The outbox row has already been deleted (e.g. concurrent relay instances): the delete should be idempotent and not throw.
- Debezium receives the same WAL entry twice due to at-least-once delivery: the consumer must tolerate a missing row gracefully.
- The CDC module (`toolkit-cdc-debezium-postgres`) must not take a compile-time dependency on `toolkit-outbox-jpa-postgres`; the delete capability must be injected via an abstraction defined in the CDC module or in `toolkit-core`.

## Acceptance Criteria

- [ ] Outbox rows are deleted from `outbox_schema.outbox` after the corresponding message is successfully published to RabbitMQ.
- [ ] A publish failure leaves the outbox row intact so Debezium can retry on reconnect.
- [ ] Restarting the CDC runtime service does not re-publish events that were already successfully relayed (with durable offset storage configured).
- [ ] Adding `snapshot.mode=no_data` to the Debezium connector configuration prevents full-table replay when the offset is missing.
- [ ] The `//TODO outbox table clean up` comment in `CloudEventRecordChangeConsumer` is removed.
- [ ] All existing tests pass; new integration tests cover the delete-after-publish path.

## Open Questions

- Should the delete be synchronous (in the same thread as publish) or deferred/batched for throughput? Synchronous is safest for correctness. So far synchronous.
- Is there a need to keep processed outbox rows for auditing? If so, a `processed_at` timestamp column and a TTL-based cleanup job may be preferable to immediate deletion. No.
- Should offset storage be moved to a PostgreSQL-backed store (Debezium `io.debezium.storage.jdbc.offset.JdbcOffsetBackingStore`) for durability, rather than relying on a file mount? This is orthogonal but should be decided before closing this feature. - Yes, use JdbcOffsetBackingStore, use plain jdbc including offset table creation.

## Testing Guidelines

Create test file(s) in the `./tests` folder (or alongside the affected modules) for the following cases:

- Verify that a successfully published message results in the outbox row being deleted.
- Verify that a publish failure leaves the outbox row in place.
- Verify that deleting an already-absent row does not throw an exception (idempotency).
- Verify that the Debezium connector configuration includes `snapshot.mode=no_data`.
- Integration test: insert a row into the outbox, let the relay process it, assert the row is gone and the message reached RabbitMQ.
