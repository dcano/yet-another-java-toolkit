package io.twba.tk.cdc.message_relay.config;

import io.twba.tk.cdc.CdcRecordChangeConsumer;
import io.twba.tk.cdc.CloudEventRecordChangeConsumer;
import io.twba.tk.cdc.DebeziumMessageRelay;
import io.twba.tk.cdc.DebeziumProperties;
import io.twba.tk.cdc.MessagePublisher;
import io.twba.tk.cdc.MessagePublisherRabbitMq;
import io.twba.tk.cdc.MessageRelay;
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
    public MessagePublisher messagePublisher(@Autowired RabbitTemplate rabbitTemplate) {
        return new MessagePublisherRabbitMq(rabbitTemplate);
    }

    @Bean
    public MessageRelay debeziumMessageRelay(@Autowired DebeziumProperties debeziumProperties,
                                             @Autowired CdcRecordChangeConsumer cdcRecordChangeConsumer) {
        return new DebeziumMessageRelay(debeziumProperties, cdcRecordChangeConsumer);
    }

    @Bean
    public CdcRecordChangeConsumer cdcRecordChangeConsumer(@Autowired MessagePublisher messagePublisher) {
        return new CloudEventRecordChangeConsumer(messagePublisher);
    }
}
