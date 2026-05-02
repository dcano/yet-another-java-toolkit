package io.twba.tk.command;

import java.time.Instant;

public interface  DomainCommand {
    String commandUid();
    Instant occurredAt();
    String commandName();
}
