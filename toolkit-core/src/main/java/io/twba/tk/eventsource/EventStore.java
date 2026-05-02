package io.twba.tk.eventsource;

import io.twba.tk.core.DomainEventPayload;
import io.twba.tk.core.Event;

import java.util.List;

public interface EventStore {

    void appendEvents(List<Event<? extends DomainEventPayload>> event);
    List<Event<DomainEventPayload>> retrieveEventsFor(String aggregateType, String aggregateId);
}
