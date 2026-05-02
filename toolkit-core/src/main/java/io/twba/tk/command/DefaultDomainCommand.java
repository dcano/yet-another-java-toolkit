package io.twba.tk.command;

import java.time.Instant;
import java.util.UUID;

public class DefaultDomainCommand implements DomainCommand {

    private final String commandUid;
    private final Instant occurredAt;
    private final String commandName;

    public DefaultDomainCommand() {
        this(UUID.randomUUID().toString(), Instant.now());
    }

    public DefaultDomainCommand(String commandUid, Instant occurredAt) {
        this.commandUid = commandUid;
        this.occurredAt = occurredAt;
        this.commandName = this.getClass().getName();
    }

    public DefaultDomainCommand(String commandUid, Instant occurredAt, String commandName) {
        this.commandUid = commandUid;
        this.occurredAt = occurredAt;
        this.commandName = commandName;
    }

    @Override
    public String commandUid() {
        return commandUid;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public String commandName() {
        return commandName;
    }
}
