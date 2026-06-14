package io.twba.tk.cdc;

import io.twba.tk.cdc.testevents.SampleEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudEventMessageConverterTest {

    private static final String CLOUD_EVENT_TYPE_HEADER = "cloudEvents_type";
    private static final String SAMPLE_EVENT_WIRE_TYPE = SampleEvent.class.getName().toLowerCase(Locale.ROOT);

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final EventRegistry eventRegistry = new EventRegistryReflection("io.twba.tk.cdc.testevents");
    private final CloudEventMessageConverter converter = new CloudEventMessageConverter(objectMapper, eventRegistry);

    @Test
    void deserializesBodyToTypeResolvedFromRegistry() {
        Message message = MessageBuilder
                .withBody("{\"aggregateId\":\"abc-123\",\"amount\":42}".getBytes(StandardCharsets.UTF_8))
                .setHeader(CLOUD_EVENT_TYPE_HEADER, SAMPLE_EVENT_WIRE_TYPE)
                .build();

        Object result = converter.fromMessage(message);

        assertThat(result).isInstanceOf(SampleEvent.class);
        SampleEvent event = (SampleEvent) result;
        assertThat(event.aggregateId()).isEqualTo("abc-123");
        assertThat(event.amount()).isEqualTo(42);
    }

    @Test
    void throwsWhenTypeHeaderMissing() {
        Message message = MessageBuilder
                .withBody("{}".getBytes(StandardCharsets.UTF_8))
                .build();

        assertThatThrownBy(() -> converter.fromMessage(message))
                .isInstanceOf(MessageConversionException.class)
                .hasMessageContaining(CLOUD_EVENT_TYPE_HEADER);
    }

    @Test
    void throwsWhenTypeHeaderBlank() {
        Message message = MessageBuilder
                .withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setHeader(CLOUD_EVENT_TYPE_HEADER, "   ")
                .build();

        assertThatThrownBy(() -> converter.fromMessage(message))
                .isInstanceOf(MessageConversionException.class);
    }

    @Test
    void throwsWhenTypeNotRegistered() {
        Message message = MessageBuilder
                .withBody("{}".getBytes(StandardCharsets.UTF_8))
                .setHeader(CLOUD_EVENT_TYPE_HEADER, "io.twba.example.unregisteredevent")
                .build();

        assertThatThrownBy(() -> converter.fromMessage(message))
                .isInstanceOf(MessageConversionException.class)
                .hasMessageContaining("unregisteredevent");
    }

    @Test
    void toMessageIsUnsupported() {
        assertThatThrownBy(() -> converter.toMessage(new SampleEvent("x", 1), new MessageProperties()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
