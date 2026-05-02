package io.twba.tk.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.twba.tk.cdc.Outbox;
import io.twba.tk.cdc.OutboxMessage;
import io.twba.tk.command.DomainCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.isNull;


public class DomainEventAppender {

    private final ThreadLocal<List<Event<? extends DomainEventPayload>>> eventsToPublish = new ThreadLocal<>();
    private final Outbox outbox;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties applicationProperties;


    public DomainEventAppender(Outbox outbox, ObjectMapper objectMapper, ApplicationProperties applicationProperties) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.applicationProperties = applicationProperties;
        resetBuffer();
    }

    public void enrichForCommand(DomainCommand command) {
        eventsToPublish.get().forEach(e -> e.setCorrelationId(CorrelationId.of(command.commandUid())));
    }

    public void append(List<Event<? extends DomainEventPayload>> events) {
        //add the event to the buffer, later this event will be published to other bounded contexts
        if(isNull(eventsToPublish.get())) {
            resetBuffer();
        }
        //ensure event is not already in buffer
        events.stream().filter(this::notInBuffer).map(this::addEventSourceMetadata).forEach(event -> eventsToPublish.get().add(event));
    }

    private Event<? extends DomainEventPayload> addEventSourceMetadata(Event<? extends DomainEventPayload> event) {
        event.setSource(applicationProperties.getName());
        return event;
    }

    public void publishToOutbox() {
        if(Objects.nonNull(eventsToPublish.get()) && !eventsToPublish.get().isEmpty()){
            eventsToPublish.get().stream().map(this::toOutboxMessage).forEach(outbox::appendMessage);
        }
        resetBuffer();
    }

    private OutboxMessage toOutboxMessage(Event<? extends DomainEventPayload> event) {
        try {
            String header = objectMapper.writeValueAsString(event.header());
            String payload = objectMapper.writeValueAsString(event.getPayload());
            return new OutboxMessage(event.getId(), header, payload, event.eventType(), Instant.now().toEpochMilli(), event.partitionKey(), event.getTenantId(), event.correlationId().value(), event.getSource(), event.getAggregateId());
        }
        catch (JsonProcessingException e) {
            throw new UnableToSerializeEventException(event.getPayload().getClass(), e);
        }
    }

    private void resetBuffer(){
        eventsToPublish.remove();
        eventsToPublish.set(new ArrayList<>());
    }

    private boolean notInBuffer(Event domainEvent) {
        return !eventsToPublish.get().contains(domainEvent);
    }

}
