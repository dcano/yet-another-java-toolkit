package io.twba.tk.eventsource;

import io.twba.tk.core.DomainEventPayload;
import io.twba.tk.core.Entity;
import io.twba.tk.core.Event;

import java.util.List;
import java.util.Optional;

public interface EventSourced<T extends Entity> {

    Optional<T> hydrateFrom(List<Event<DomainEventPayload>> events);

}
