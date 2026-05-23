package io.twba.tk.cdc.outbox;

import tools.jackson.databind.json.JsonMapper;
import io.twba.tk.cdc.OutboxProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestAppConfig {

    @Bean
    public OutboxProperties outboxProperties() {
        OutboxProperties outboxProperties = new OutboxProperties();
        outboxProperties.setNumPartitions(2);
        return outboxProperties;
    }


    @Bean
    public JsonMapper objectMapper() {
        return JsonMapper.builder().build();
    }

}
