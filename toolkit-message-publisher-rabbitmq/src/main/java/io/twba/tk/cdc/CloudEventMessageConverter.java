package io.twba.tk.cdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.ObjectMapper;

import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_AMQP_BINDING_PREFIX;
import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_TYPE;

/**
 * Consumer-side {@link MessageConverter} that reads the {@code cloudEvents_type} AMQP
 * application property and deserializes the message body to the concrete domain event
 * class resolved from that type by an {@link EventRegistry}.
 *
 * <p>The toolkit publishes the event type as a lowercased fully-qualified class name, so a
 * plain {@code Class.forName} cannot recover the class. Instead the consumer registers its
 * domain events with {@link io.twba.tk.event.EventDefinition} and supplies an
 * {@link EventRegistry} that maps the wire type back to the class.
 *
 * <p>This converter is <strong>not</strong> registered via auto-configuration. Consuming
 * services must explicitly wire it into their listener container factory. Existing
 * {@code @RabbitListener} methods that do not reference it continue to work unchanged.
 *
 * <p>It is consumer-side only: {@link #toMessage(Object, MessageProperties)} is unsupported.
 */
public class CloudEventMessageConverter implements MessageConverter {

    private static final Logger log = LoggerFactory.getLogger(CloudEventMessageConverter.class);

    private static final String CLOUD_EVENT_TYPE_HEADER = CLOUD_EVENT_AMQP_BINDING_PREFIX + CLOUD_EVENT_TYPE;

    private final ObjectMapper objectMapper;
    private final EventRegistry eventRegistry;

    public CloudEventMessageConverter(ObjectMapper objectMapper, EventRegistry eventRegistry) {
        this.objectMapper = objectMapper;
        this.eventRegistry = eventRegistry;
    }

    @Override
    public Message toMessage(Object object, MessageProperties messageProperties) {
        throw new UnsupportedOperationException("CloudEventMessageConverter is consumer-side only and cannot serialize messages");
    }

    @Override
    public Object fromMessage(Message message) throws MessageConversionException {
        Object header = message.getMessageProperties().getHeaders().get(CLOUD_EVENT_TYPE_HEADER);
        String cloudEventType = header != null ? header.toString() : null;

        if (cloudEventType == null || cloudEventType.isBlank()) {
            log.warn("Received AMQP message without a '{}' header; cannot resolve the target domain event type", CLOUD_EVENT_TYPE_HEADER);
            throw new MessageConversionException("Missing or blank '" + CLOUD_EVENT_TYPE_HEADER + "' header");
        }

        Class<?> targetType = eventRegistry.classFor(cloudEventType)
                .orElseThrow(() -> {
                    log.warn("No @EventDefinition registered for type '{}'; cannot deserialize message", cloudEventType);
                    return new MessageConversionException("No @EventDefinition registered for type '" + cloudEventType + "'");
                });

        try {
            return objectMapper.readValue(message.getBody(), targetType);
        } catch (Exception e) {
            throw new MessageConversionException("Failed to deserialize message body to " + targetType.getName(), e);
        }
    }
}
