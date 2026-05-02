package io.twba.tk.cdc.outbox.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(schema="outbox_schema", name = "outbox",
        indexes = {
                @Index(name = "idx_outbox_type", columnList = "type"),
                @Index(name = "idx_outbox_epoch", columnList = "epoch")
        })
public class OutboxMessageEntity {

    @Id
    String uuid;
    @JdbcTypeCode(SqlTypes.JSON)
    String metadata;
    @JdbcTypeCode(SqlTypes.JSON)
    String payload;
    String type;
    long epoch;
    String partitionKey;
    int partition;
    String tenantId;
    String correlationId;
    String source;
    String aggregateId;
}
