package io.twba.tk.eventsource;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import io.twba.tk.core.DomainEventPayload;
import io.twba.tk.core.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventStoreJdbcPostgres implements EventStore, Closeable {

    private final Logger logger = LoggerFactory.getLogger(EventStoreJdbcPostgres.class);

    private static final String INSERT_EVENT_SQL = """
        INSERT INTO event_sourcing_schema.event_store (
            uuid, aggregate_type, aggregate_id, type, payload,
            tenant_id, event_epoch, event_stream_version, system_epoch, event_class_name
        ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
        """;

    private static final String SELECT_EVENTS_SQL = """
        SELECT type, payload, event_class_name
        FROM event_sourcing_schema.event_store
        WHERE aggregate_type = ? AND aggregate_id = ?
        ORDER BY event_stream_version ASC
        """;

    private final JsonMapper objectMapper;
    private final Connection connection;

    public EventStoreJdbcPostgres(DataSource dataSource, JsonMapper objectMapper) throws SQLException {
        this.objectMapper = ensureJavaTimeSupport(objectMapper);
        this.connection = dataSource.getConnection();
    }

    public EventStoreJdbcPostgres(Connection connection, JsonMapper objectMapper) throws SQLException {
        this.objectMapper = ensureJavaTimeSupport(objectMapper);
        this.connection = connection;
    }

    @Override
    public void appendEvents(List<Event<? extends DomainEventPayload>> events) {
        try (PreparedStatement ps = connection.prepareStatement(INSERT_EVENT_SQL)) {

            for (Event<? extends DomainEventPayload> event : events) {
                ps.setString(1, event.getId());
                ps.setString(2, event.getAggregateType());
                ps.setString(3, event.getAggregateId());
                ps.setString(4, event.eventType());
                ps.setString(5, objectMapper.writeValueAsString(event.getPayload()));
                ps.setString(6, event.getTenantId());
                ps.setLong(7, event.getPayload().getOccurredOn().toEpochMilli());
                ps.setLong(8, event.getEventStreamVersion());
                ps.setLong(9, Instant.now().toEpochMilli());
                ps.setString(10, event.getPayload().getClass().getName());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException | JacksonException e) {
            throw new EventStoreException("Failed to append events to the event store", e);
        }
    }

    @Override
    public List<Event<DomainEventPayload>> retrieveEventsFor(String aggregateType, String aggregateId) {
        List<Event<DomainEventPayload>> events = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT_EVENTS_SQL)) {

            ps.setString(1, aggregateType);
            ps.setString(2, aggregateId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(reconstituteEvent(rs.getString("event_class_name"), rs.getString("payload"), rs.getString("type")));
                }
            }
        } catch (SQLException e) {
            throw new EventStoreException("Failed to retrieve events for aggregate: " + aggregateId, e);
        }
        return events;
    }

    private Event<DomainEventPayload> reconstituteEvent(String eventClassName, String payloadJson, String eventType) {
        try {
            Class<?> payloadClass = Class.forName(eventClassName);
            DomainEventPayload payload = (DomainEventPayload) objectMapper.readValue(payloadJson, payloadClass);

            return new Event<>(payload, eventType);
        } catch (JacksonException | ClassNotFoundException | ClassCastException e) {
            throw new EventStoreException("Failed to deserialize event of type: " + eventClassName, e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException e) {
            logger.warn("Unable to close event store connection", e);
            throw new RuntimeException(e);
        }
    }

    private static JsonMapper ensureJavaTimeSupport(JsonMapper baseMapper) {
        JsonMapper.Builder builder = (baseMapper == null) ? JsonMapper.builder() : baseMapper.rebuild();

        // Ensure Instant works — registers an ISO-8601 serializer/deserializer via the builder.
        SimpleModule javaTimeFallback = new SimpleModule("java-time-fallback");
        javaTimeFallback.addSerializer(Instant.class, new ValueSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializationContext serializers) throws JacksonException {
                if (value == null) {
                    gen.writeNull();
                    return;
                }
                gen.writeString(value.toString());
            }
        });
        javaTimeFallback.addDeserializer(Instant.class, new ValueDeserializer<Instant>() {
            @Override
            public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
                String text = p.getValueAsString();
                return (text == null || text.isBlank()) ? null : Instant.parse(text);
            }
        });

        return builder.addModule(javaTimeFallback).build();
    }

}
