package io.twba.tk.core;

import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
public abstract class DomainEventPayload implements Partitionable {

    /**
     * When the event was created in UIC
     */
    private final Instant occurredOn;

    /**
     * Randomly generated UID for created events
     */
    private final String eventUid;

    /**
     * TenandId for the domain event
     */
    private final String tenantId;

    protected DomainEventPayload(String tenantId) {
        this.occurredOn = Instant.now();
        this.eventUid = UUID.randomUUID().toString();
        this.tenantId = tenantId;
    }

    protected DomainEventPayload() {
        this(null);
    }

    protected DomainEventPayload(Instant occurredOn, String eventUid, String tenantId) {
        this.occurredOn = occurredOn;
        this.eventUid = eventUid;
        this.tenantId = tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DomainEventPayload that = (DomainEventPayload) o;
        return Objects.equals(eventUid, that.eventUid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventUid);
    }
}
