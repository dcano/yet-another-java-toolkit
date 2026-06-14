package io.twba.tk.cdc.testevents;

import io.twba.tk.event.EventDefinition;

@EventDefinition
public record SampleEvent(String aggregateId, int amount) {
}
