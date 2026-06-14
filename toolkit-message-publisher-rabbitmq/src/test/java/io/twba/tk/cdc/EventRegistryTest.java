package io.twba.tk.cdc;

import io.twba.tk.cdc.testevents.CustomTypeEvent;
import io.twba.tk.cdc.testevents.PlainPojo;
import io.twba.tk.cdc.testevents.SampleEvent;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EventRegistryTest {

    private static final String BASE_PACKAGE = "io.twba.tk.cdc.testevents";

    private final EventRegistry registry = new EventRegistryReflection(BASE_PACKAGE);

    @Test
    void resolvesAnnotatedEventByLowercasedFqn() {
        String wireType = SampleEvent.class.getName().toLowerCase(Locale.ROOT);

        assertThat(registry.classFor(wireType)).contains(SampleEvent.class);
    }

    @Test
    void resolvesAnnotatedEventByExplicitDeclaredType() {
        assertThat(registry.classFor("io.twba.example.customdeclaredevent")).contains(CustomTypeEvent.class);
    }

    @Test
    void ignoresClassesWithoutEventDefinition() {
        String wireType = PlainPojo.class.getName().toLowerCase(Locale.ROOT);

        assertThat(registry.classFor(wireType)).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownOrBlankType() {
        assertThat(registry.classFor("io.twba.unknown.nope")).isEmpty();
        assertThat(registry.classFor(null)).isEmpty();
        assertThat(registry.classFor("  ")).isEmpty();
    }

    @Test
    void registersOnlyTheAnnotatedEvents() {
        assertThat(registry.size()).isEqualTo(2);
    }
}
