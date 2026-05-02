package io.twba.tk.eventsource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.twba.tk.core.DomainEventPayload;
import io.twba.tk.core.Event;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class EventStoreJdbcPostgresTest {

    public static final String DB_NAME = "test_db";
    public static final String DB_USERNAME = "sa";
    public static final String DB_PASSWORD = "sa";


    @Container
    public static PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USERNAME)
            .withPassword(DB_PASSWORD);


    @BeforeAll
    static void migrateDatabase() {
        Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() {

    }

    @Test
    void testRuntimeStartup() throws SQLException {
        String jdbcUrl = container.getJdbcUrl();
        String username = container.getUsername();
        String password = container.getPassword();

        // get a plain JDBC Connection from DriverManager
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {


            EventStoreJdbcPostgres eventStore = new EventStoreJdbcPostgres(conn, new ObjectMapper());

            List<Event<? extends DomainEventPayload>> eventsToAppend = Arrays.asList(new Event<>(new TestEvent("1")),
                    new Event<>(new TestEvent("2")));

            eventStore.appendEvents(eventsToAppend);

            try(PreparedStatement ps = conn.prepareStatement("SELECT count(*) from event_sourcing_schema.event_store");
                ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    int value = rs.getInt(1);
                    assertEquals(eventsToAppend.size(), value);
                } else {
                    throw new AssertionError("No rows returned from SELECT 1");
                }
            }

            eventStore.retrieveEventsFor(TestEvent.class.getSimpleName(), "unknown").forEach(o -> {
                assertThat(eventsToAppend.contains(o));
            });

        }
    }

    static class TestEvent extends DomainEventPayload {

        private String value;

        TestEvent(String value) {
            this.value = value;
        }

        public TestEvent() {

        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String partitionKey() {
            return "test-key";
        }



    }

}
