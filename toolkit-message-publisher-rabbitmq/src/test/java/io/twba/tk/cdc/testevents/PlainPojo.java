package io.twba.tk.cdc.testevents;

/**
 * Not annotated with {@code @EventDefinition} — the registry must ignore it.
 */
public record PlainPojo(String value) {
}
