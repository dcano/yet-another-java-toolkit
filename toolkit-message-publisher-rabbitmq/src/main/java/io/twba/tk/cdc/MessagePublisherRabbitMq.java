package io.twba.tk.cdc;

import io.cloudevents.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Objects;

import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_AMQP_BINDING_PREFIX;
import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_GENERATING_APP_NAME;
import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_PARTITION_KEY;
import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_SOURCE;
import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_SUBJECT;
import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_TENANT_ID;
import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_TIMESTAMP;
import static io.twba.tk.event.TwbaCloudEvent.CLOUD_EVENT_TYPE;

public class MessagePublisherRabbitMq implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisherRabbitMq.class);

    private final RabbitTemplate rabbitTemplate;

    public MessagePublisherRabbitMq(RabbitTemplate rabbitTemplate) {
        this(rabbitTemplate, false);
    }

    public MessagePublisherRabbitMq(RabbitTemplate rabbitTemplate, boolean returnsEnabled) {
        this.rabbitTemplate = rabbitTemplate;
        if (returnsEnabled) {
            rabbitTemplate.setMandatory(true);
            rabbitTemplate.setReturnsCallback(returned ->
                    log.error("Unroutable message returned by broker: exchange='{}', routingKey='{}', replyCode={}, replyText='{}', messageId='{}'",
                            returned.getExchange(),
                            returned.getRoutingKey(),
                            returned.getReplyCode(),
                            returned.getReplyText(),
                            returned.getMessage().getMessageProperties().getMessageId()));
        }
    }

    @Override
    public boolean publish(CloudEvent dispatchedMessage) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

        rabbitTemplate.send("__MR__" + dispatchedMessage.getExtension(CLOUD_EVENT_GENERATING_APP_NAME),
                dispatchedMessage.getType(),
                toAmqpMessage(dispatchedMessage));

        return true;
    }

    private static Message toAmqpMessage(CloudEvent dispatchedMessage) {
        return MessageBuilder.withBody(Objects.nonNull(dispatchedMessage.getData()) ? dispatchedMessage.getData().toBytes() : new byte[0])
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(dispatchedMessage.getId())
                .setHeader(CLOUD_EVENT_AMQP_BINDING_PREFIX + CLOUD_EVENT_TYPE, dispatchedMessage.getType())
                .setHeader(CLOUD_EVENT_AMQP_BINDING_PREFIX + CLOUD_EVENT_TENANT_ID, dispatchedMessage.getExtension(CLOUD_EVENT_TENANT_ID))
                .setHeader(CLOUD_EVENT_AMQP_BINDING_PREFIX + CLOUD_EVENT_TIMESTAMP, dispatchedMessage.getExtension(CLOUD_EVENT_TIMESTAMP))
                .setHeader(CLOUD_EVENT_AMQP_BINDING_PREFIX + CLOUD_EVENT_PARTITION_KEY, dispatchedMessage.getExtension(CLOUD_EVENT_PARTITION_KEY))
                .setHeader(CLOUD_EVENT_AMQP_BINDING_PREFIX + CLOUD_EVENT_SUBJECT, dispatchedMessage.getSubject())
                .setHeader(CLOUD_EVENT_AMQP_BINDING_PREFIX + CLOUD_EVENT_SOURCE, dispatchedMessage.getSource().toString())
                .build();
    }

}
