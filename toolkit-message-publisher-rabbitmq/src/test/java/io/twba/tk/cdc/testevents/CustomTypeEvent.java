package io.twba.tk.cdc.testevents;

import io.twba.tk.event.EventDefinition;

@EventDefinition(type = "io.twba.example.CustomDeclaredEvent")
public record CustomTypeEvent(String id) {
}
