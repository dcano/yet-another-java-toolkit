package io.twba.tk.cdc.outbox;


import io.twba.tk.cdc.Outbox;
import io.twba.tk.cdc.OutboxMessage;
import io.twba.tk.cdc.OutboxProperties;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageEntity;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageRepositoryJpaHelper;
import org.apache.commons.codec.digest.MurmurHash3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OutboxJpa implements Outbox {

    private final OutboxProperties outboxProperties;
    private final OutboxMessageRepositoryJpaHelper helper;

    @Autowired
    public OutboxJpa(OutboxProperties outboxProperties, OutboxMessageRepositoryJpaHelper helper) {
        this.outboxProperties = outboxProperties;
        this.helper = helper;
    }

    @Override
    public void appendMessage(OutboxMessage outboxMessage) {
        helper.save(toJpa(outboxMessage));
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
