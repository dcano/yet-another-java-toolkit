package io.twba.tk.cdc;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.twba.tk.cdc.CloudEventRecordChangeConsumer.READ_COUNTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CloudEventRecordChangeConsumerTest {

    private final MessagePublisher messagePublisher = mock(MessagePublisher.class);

    @Test
    void countsReadMessagesWhenMeterRegistryProvided() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CloudEventRecordChangeConsumer consumer = new CloudEventRecordChangeConsumer(messagePublisher, registry);

        consumer.accept(cdcRecord());
        consumer.accept(cdcRecord());

        assertThat(registry.get(READ_COUNTER).counter().count()).isEqualTo(2.0);
        verify(messagePublisher, times(2)).publish(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void worksWithoutMeterRegistry() {
        CloudEventRecordChangeConsumer consumer = new CloudEventRecordChangeConsumer(messagePublisher);

        assertThatCode(() -> consumer.accept(cdcRecord())).doesNotThrowAnyException();
        verify(messagePublisher).publish(org.mockito.ArgumentMatchers.any());
    }

    private static CdcRecord cdcRecord() {
        Map<String, Object> values = new HashMap<>();
        values.put("payload", "{\"orderId\":\"o-1\"}");
        values.put("uuid", "11111111-1111-1111-1111-111111111111");
        values.put("type", "com.acme.orders.events.orderplaced");
        values.put("tenant_id", "tenant-1");
        values.put("aggregate_id", "agg-1");
        values.put("epoch", 1_700_000_000_000L);
        values.put("partition_key", "pk-1");
        values.put("source", "order-management");
        values.put("correlation_id", "corr-1");
        return new MapCdcRecord(values);
    }

    private record MapCdcRecord(Map<String, Object> values) implements CdcRecord {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T valueOf(String key) {
            return (T) values.get(key);
        }
    }
}
