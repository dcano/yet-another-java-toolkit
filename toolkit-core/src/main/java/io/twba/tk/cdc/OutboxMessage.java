package io.twba.tk.cdc;

public record OutboxMessage(String uuid,
                            String header,
                            String payload,
                            String type,
                            long epoch,
                            String partitionKey,
                            String tenantId,
                            String correlationId,
                            String source,
                            String aggregateId) {
}
