package io.twba.tk.cdc.message_relay.config;

import io.twba.tk.cdc.CdcRecordChangeConsumer;
import io.twba.tk.cdc.CloudEventRecordChangeConsumer;
import io.twba.tk.cdc.DebeziumMessageRelay;
import io.twba.tk.cdc.DebeziumProperties;
import io.twba.tk.cdc.MessagePublisher;
import io.twba.tk.cdc.MessagePublisherRabbitMq;
import io.twba.tk.cdc.MessageRelay;
import io.twba.tk.cdc.MessageRelayProps;
import io.twba.tk.cdc.OutboxCleaner;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DebeziumConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "debezium")
    public DebeziumProperties debeziumProperties() {
        return new DebeziumProperties();
    }

    @Bean
    public MessagePublisher messagePublisher(@Autowired RabbitTemplate rabbitTemplate,
                                             @Autowired ConnectionFactory connectionFactory,
                                             @Autowired MessageRelayProps messageRelayProps) {
        boolean returnsEnabled = messageRelayProps.isPublisherReturns();
        // ReturnsCallback only fires when the connection factory has publisher returns enabled.
        if (returnsEnabled && connectionFactory instanceof CachingConnectionFactory cachingConnectionFactory) {
            cachingConnectionFactory.setPublisherReturns(true);
        }
        return new MessagePublisherRabbitMq(rabbitTemplate, returnsEnabled);
    }

    @Bean
    public MessageRelay debeziumMessageRelay(@Autowired DebeziumProperties debeziumProperties,
                                             @Autowired CdcRecordChangeConsumer cdcRecordChangeConsumer) {
        return new DebeziumMessageRelay(debeziumProperties, cdcRecordChangeConsumer);
    }

    @Bean
    public CdcRecordChangeConsumer cdcRecordChangeConsumer(@Autowired MessagePublisher messagePublisher,
                                                           @Autowired(required = false) MeterRegistry meterRegistry,
                                                           @Autowired OutboxCleaner outboxCleaner) {
        return new CloudEventRecordChangeConsumer(messagePublisher, meterRegistry, outboxCleaner);
    }
}
