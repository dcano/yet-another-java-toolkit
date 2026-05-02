package io.twba.tk.cdc;

public interface Outbox {

    void appendMessage(OutboxMessage outboxMessage);
    int partitionFor(String partitionKey);
}
