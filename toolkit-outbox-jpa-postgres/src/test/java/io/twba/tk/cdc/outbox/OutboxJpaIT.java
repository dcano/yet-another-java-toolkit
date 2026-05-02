package io.twba.tk.cdc.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.twba.tk.cdc.OutboxMessage;
import io.twba.tk.cdc.outbox.config.PersistenceConfigOutbox;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageEntity;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageRepositoryJpaHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.NoSuchElementException;

import static io.twba.tk.cdc.outbox.OutboxMessages.randomOutboxMessage;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@ContextConfiguration(classes = {PersistenceConfigOutbox.class, TestAppConfig.class})
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class OutboxJpaIT {

    @Autowired
    public OutboxJpa outboxJpa;

    @Autowired
    public OutboxMessageRepositoryJpaHelper helper;

    @Container
    public static PostgreSQLContainer container = new PostgreSQLContainer("postgres:latest")
            .withDatabaseName("outbox_db")
            .withUsername("sa")
            .withPassword("sa");


    @DynamicPropertySource
    public static void overrideProperties(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.driver-class-name", container::getDriverClassName);
    }

    @Test
    public void shouldPersistsMessageInOutboxDatabase() throws JsonProcessingException {
        OutboxMessage expectedMessage = randomOutboxMessage();
        outboxJpa.appendMessage(expectedMessage);
        OutboxMessageEntity actualMessage = helper.findById(expectedMessage.uuid()).orElseThrow(NoSuchElementException::new);
        assertAll("Store outbox message in postgres database",
                () -> assertEquals(expectedMessage.epoch(), actualMessage.getEpoch()),
                () -> assertEquals(expectedMessage.uuid(), actualMessage.getUuid()),
                () -> assertNotNull(expectedMessage.payload(), actualMessage.getPayload()));
    }

}
