package io.twba.tk.cdc.outbox.config;

import io.twba.tk.cdc.Outbox;
import io.twba.tk.cdc.OutboxProperties;
import io.twba.tk.cdc.outbox.OutboxJpa;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageRepositoryJpaHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = {
        "io.twba.tk.cdc"
})
@EntityScan(basePackages = {
        "io.twba.tk.cdc"
})
@EnableJpaRepositories(basePackages = {
        "io.twba.tk.cdc"
})
public class PersistenceConfigOutbox {

    public Outbox outbox(@Autowired OutboxMessageRepositoryJpaHelper helper,
                         @Autowired OutboxProperties outboxProperties) {
        return new OutboxJpa(outboxProperties, helper);
    }

}
