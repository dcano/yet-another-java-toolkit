package io.twba.tk.core;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Event<T extends DomainEventPayload> implements Versionable, Traceable, Routable {

    private static final String CORRELATION_ID = "__correlation_id__";
    private static final String ROUTING_KEY = "__routing_key__";
    private static final String VERSION = "__version__";
    private static final String AGGREGATE_TYPE = "__aggregate_type__";
    private static final String AGGREGATE_ID = "__aggregate_id__";
    private static final String EVENT_STREAM_VERSION = "__event_stream_version__";
    private static final String PARTITION_KEY = "__partition_key__";
    private static final String SOURCE = "__source__";
    private final Map<String, Object> header;
    @Getter
    private final T payload;


    public Event(T payload) {
        this(payload, payload.getClass().getName());
    }

    public Event(T payload, String eventType) {
        this(new HashMap<>(), payload, eventType);
    }

    public Event(Map<String, Object> header, T payload, String eventType) {
        this(header, payload, RoutingKey.from(eventType));
    }

    private Event(Map<String, Object> header, T payload, RoutingKey routingKey) {
        this.header = header;
        this.payload = payload;
        this.header.put(ROUTING_KEY, routingKey.toString());
        this.header.put(PARTITION_KEY, payload.partitionKey());
        if(!header.containsKey(AGGREGATE_TYPE)) {
            this.header.put(AGGREGATE_TYPE, payload.getClass().getSimpleName());
        }
        if(!header.containsKey(AGGREGATE_ID)) {
            this.header.put(AGGREGATE_ID, "unknown");
        }
    }


    @Override
    public void setVersion(ApplicationVersion applicationVersion) {
        this.header.put(VERSION, applicationVersion.getVersion());
    }

    @Override
    public ApplicationVersion getVersion() {
        return ApplicationVersion.of((String)header.get(VERSION));
    }

    @Override
    public void setCorrelationId(CorrelationId correlationId) {
        header.put(CORRELATION_ID, correlationId.value());
    }

    @Override
    public CorrelationId correlationId() {
        return CorrelationId.of((String)header.get(CORRELATION_ID));
    }


    @Override
    public RoutingKey getRoutingKey() {
        return RoutingKey.from((String)header.get(ROUTING_KEY));
    }

    public String getId() {
        return  getPayload().getEventUid();
    }

    public void setAggregateType(String aggregateType) {
        header.put(AGGREGATE_TYPE, aggregateType);
    }

    public String getAggregateType() {
        return (String)header.get(AGGREGATE_TYPE);
    }

    public void setAggregateId(String aggregateId) {
        header.put(AGGREGATE_ID, aggregateId);
    }

    public String getAggregateId() {
        return (String)header.get(AGGREGATE_ID);
    }

    public void setEventStreamVersion(long version) {
        header.put(EVENT_STREAM_VERSION, version);
    }

    public long getEventStreamVersion() {
        if(!header.containsKey(EVENT_STREAM_VERSION)) {
            return 0L;
        }
        return (Long)header.get(EVENT_STREAM_VERSION);
    }

    public String getTenantId() {
        return payload.getTenantId();
    }


    public String eventType() {
        return getRoutingKey().toString();
    }

    String partitionKey() {
        return (String)header.get(PARTITION_KEY);
    }

    Map<String, Object> header() {
        return new HashMap<>(header);
    }

    public void setSource(String source) {
        header.put(SOURCE, source);
    }

    public String getSource() {
        return (String)header.get(SOURCE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (Event<?>) o;
        return this.getId().equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId());
    }

}
