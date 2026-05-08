package io.twba.tk.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.twba.tk.command.CommandBus;
import io.twba.tk.command.CommandBusInProcess;
import io.twba.tk.command.CommandHandler;
import io.twba.tk.command.DomainCommand;
import io.twba.tk.configure.ToolkitProperties;
import io.twba.tk.core.DomainEventAppender;
import io.twba.tk.core.TwbaTransactionManager;
import io.twba.tk.eventsource.EventStore;
import io.twba.tk.eventsource.EventStoreJdbcPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

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

    @Bean
    @ConditionalOnMissingBean(CommandBus.class)
    @ConditionalOnBean({TwbaTransactionManager.class, DomainEventAppender.class})
    public CommandBusInProcess commandBus(List<CommandHandler<? extends DomainCommand>> commandHandlers,
                                          DomainEventAppender domainEventAppender,
                                          TwbaTransactionManager transactionManager,
                                          ToolkitProperties toolkitProperties,
                                          @Autowired(required = false) MeterRegistry meterRegistry) {
        return new CommandBusInProcess(commandHandlers, domainEventAppender, transactionManager,
                toolkitProperties, meterRegistry);
    }
}
