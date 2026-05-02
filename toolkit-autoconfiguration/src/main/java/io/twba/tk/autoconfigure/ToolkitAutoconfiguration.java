package io.twba.tk.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.twba.tk.configure.ToolkitProperties;
import io.twba.tk.eventsource.EventStore;
import io.twba.tk.eventsource.EventStoreJdbcPostgres;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

@EnableConfigurationProperties
@Configuration
public class ToolkitAutoconfiguration {

    @Bean
    @ConfigurationProperties(prefix = "io.twba.tk.properties")
    public ToolkitProperties toolkitProperties() {
        return new ToolkitProperties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "io.twba.tk.properties.event-sourcing", name = "type", havingValue = "postgres")
    public EventStore jdbcPostgresEventStore(DataSource dataSource, ObjectMapper objectMapper) throws SQLException {
        return new EventStoreJdbcPostgres(dataSource, objectMapper);
    }

}
