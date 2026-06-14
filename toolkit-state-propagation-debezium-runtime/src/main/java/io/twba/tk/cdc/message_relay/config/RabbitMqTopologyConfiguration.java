package io.twba.tk.cdc.message_relay.config;

import io.twba.tk.cdc.MessageRelayProps;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfiguration {

    @Bean
    TopicExchange exchange(@Autowired MessageRelayProps messageRelayProps) {
        // durable=true, autoDelete=false so the exchange survives broker restarts.
        // RabbitAdmin (from spring-boot-starter-amqp) declares this bean on connection.
        return new TopicExchange("__MR__" + messageRelayProps.getServiceName(), true, false);
    }

    @Bean
    @ConfigurationProperties(prefix = "cdc")
    public MessageRelayProps messageRelayProps() {
        return new MessageRelayProps();
    }
}
