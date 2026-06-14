package io.twba.tk.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a domain event class so it can be discovered by an {@code EventRegistry} via
 * classpath scanning and mapped to the event type carried on the wire (the CloudEvent
 * {@code type} / {@code cloudEvents_type} AMQP header).
 *
 * <p>Applications using the toolkit annotate each consumable domain event with this
 * annotation. The registry then resolves the concrete class from the (lowercased) type
 * string at message-conversion time, so consumers never need to call {@code Class.forName}
 * on the raw header.
 *
 * <p>{@link #type()} may be left blank, in which case the registry derives the type from
 * the lowercased fully-qualified class name — matching the default type emitted by the
 * toolkit publisher. Set an explicit value only when the producer emits a custom type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventDefinition {

    String type() default "";

}
