package io.twba.tk.cdc.message_relay.config;

import io.twba.tk.cdc.MessageRelayProps;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(value={"dev"})
public class RabbitMqTopologyConfiguration {

    @Bean
    TopicExchange exchange(@Autowired MessageRelayProps messageRelayProps) {
        return new TopicExchange("__MR__" + messageRelayProps.getServiceName());
    }

    //TODO non dev profile must create the exchange statically in the runtime of the service that generates the messages (course-management)
    @Bean
    @ConfigurationProperties(prefix = "cdc")
    public MessageRelayProps messageRelayProps() {
        return new MessageRelayProps();
    }
}
