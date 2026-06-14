package io.twba.tk.cdc.outbox;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.twba.tk.cdc.Outbox;
import io.twba.tk.cdc.OutboxMessage;
import io.twba.tk.cdc.OutboxProperties;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageEntity;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageRepositoryJpaHelper;
import io.twba.tk.configure.ToolkitProperties;
import org.apache.commons.codec.digest.MurmurHash3;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OutboxJpa implements Outbox {

    /**
     * Counts every domain event message written to the outbox table. Paired with
     * {@code twba.outbox.messages.read} (incremented by the Debezium CDC consumer) it lets
     * you track the ratio of generated vs consumed events.
     */
    public static final String PUBLISHED_COUNTER = "twba.outbox.messages.published";

    private final OutboxProperties outboxProperties;
    private final OutboxMessageRepositoryJpaHelper helper;
    private final Counter publishedCounter;

    @Autowired
    public OutboxJpa(OutboxProperties outboxProperties,
                     OutboxMessageRepositoryJpaHelper helper,
                     ObjectProvider<MeterRegistry> meterRegistryProvider,
                     ObjectProvider<ToolkitProperties> toolkitPropertiesProvider) {
        this.outboxProperties = outboxProperties;
        this.helper = helper;
        this.publishedCounter = buildCounter(meterRegistryProvider.getIfAvailable(), toolkitPropertiesProvider.getIfAvailable());
    }

    public OutboxJpa(OutboxProperties outboxProperties, OutboxMessageRepositoryJpaHelper helper) {
        this(outboxProperties, helper, (MeterRegistry) null, (ToolkitProperties) null);
    }

    public OutboxJpa(OutboxProperties outboxProperties,
                     OutboxMessageRepositoryJpaHelper helper,
                     MeterRegistry meterRegistry,
                     ToolkitProperties toolkitProperties) {
        this.outboxProperties = outboxProperties;
        this.helper = helper;
        this.publishedCounter = buildCounter(meterRegistry, toolkitProperties);
    }

    private static Counter buildCounter(MeterRegistry meterRegistry, ToolkitProperties toolkitProperties) {
        boolean enabled = toolkitProperties != null
                && toolkitProperties.getInstrumentation() != null
                && toolkitProperties.getInstrumentation().isEnabled();
        if (!enabled || meterRegistry == null) {
            return null;
        }
        return Counter.builder(PUBLISHED_COUNTER)
                .description("Total domain event messages written to the outbox table")
                .register(meterRegistry);
    }

    @Override
    public void appendMessage(OutboxMessage outboxMessage) {
        helper.save(toJpa(outboxMessage));
        if (publishedCounter != null) {
            publishedCounter.increment();
        }
    }

    @Override
    public int partitionFor(String partitionKey) {
        return MurmurHash3.hash32x86(partitionKey.getBytes()) % outboxProperties.getNumPartitions();
    }

    private OutboxMessageEntity toJpa(OutboxMessage outboxMessage) {
        OutboxMessageEntity outboxMessageEntity = new OutboxMessageEntity();
        outboxMessageEntity.setMetadata(outboxMessage.header());
        outboxMessageEntity.setUuid(outboxMessage.uuid());
        outboxMessageEntity.setType(outboxMessage.type());
        outboxMessageEntity.setPartitionKey(outboxMessage.partitionKey());
        outboxMessageEntity.setPayload(outboxMessage.payload());
        outboxMessageEntity.setPartition(this.partitionFor(outboxMessage.partitionKey()));
        outboxMessageEntity.setEpoch(outboxMessage.epoch());
        outboxMessageEntity.setTenantId(outboxMessage.tenantId());
        outboxMessageEntity.setAggregateId(outboxMessage.aggregateId());
        outboxMessageEntity.setCorrelationId(outboxMessage.correlationId());
        outboxMessageEntity.setSource(outboxMessage.source());
        return outboxMessageEntity;
    }
}
