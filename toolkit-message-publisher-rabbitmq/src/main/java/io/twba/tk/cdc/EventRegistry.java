package io.twba.tk.cdc;

import io.twba.tk.event.EventDefinition;

import java.util.Optional;

/**
 * Resolves a domain event {@link Class} from the (lowercased) event type carried on the
 * wire (the CloudEvent {@code type} / {@code cloudEvents_type} AMQP header).
 *
 * <p>Implementations index the domain events a consumer can handle — typically those
 * annotated with {@link EventDefinition} — so {@link CloudEventMessageConverter} can
 * deserialize a message body to the right type without calling {@code Class.forName} on
 * the raw header.
 *
 * @see EventRegistryReflection
 */
public interface EventRegistry {

    /**
     * Resolves the event class registered for the given wire type, or empty if none.
     */
    Optional<Class<?>> classFor(String type);

    /**
     * Number of registered event types.
     */
    int size();
}
