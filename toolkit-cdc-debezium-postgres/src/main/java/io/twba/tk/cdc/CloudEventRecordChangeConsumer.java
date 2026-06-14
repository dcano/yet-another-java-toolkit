package io.twba.tk.cdc;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.twba.tk.event.TwbaCloudEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;

import java.net.URI;


public class CloudEventRecordChangeConsumer implements CdcRecordChangeConsumer {

    /**
     * Counts every outbox row read from the database and relayed by the Debezium CDC
     * engine. Paired with {@code twba.outbox.messages.published} (incremented when a
     * message is written to the outbox) it lets you track the ratio of generated vs
     * consumed events.
     */
    public static final String READ_COUNTER = "twba.outbox.messages.read";

    private final MessagePublisher messagePublisher;
    private final Counter readCounter;

    public CloudEventRecordChangeConsumer(MessagePublisher messagePublisher) {
        this(messagePublisher, null);
    }

    public CloudEventRecordChangeConsumer(MessagePublisher messagePublisher, MeterRegistry meterRegistry) {
        this.messagePublisher = messagePublisher;
        this.readCounter = meterRegistry != null
                ? Counter.builder(READ_COUNTER)
                    .description("Total outbox messages read from the database and relayed by the Debezium CDC consumer")
                    .register(meterRegistry)
                : null;
    }

    @Override
    public void accept(CdcRecord cdcRecord) {

        final CloudEvent event;

        try {
            if (readCounter != null) {
                readCounter.increment();
            }
            //TODO outbox table clean up
            String payload = cdcRecord.valueOf("payload");
            String uuid = cdcRecord.valueOf("uuid");
            String type = cdcRecord.valueOf("type");
            String tenantId = cdcRecord.valueOf("tenant_id");
            String aggregateId = cdcRecord.valueOf("aggregate_id");
            long epoch = cdcRecord.valueOf("epoch");
            String partitionKey = cdcRecord.valueOf("partition_key");
            String source = cdcRecord.valueOf("source");
            String correlationId = cdcRecord.valueOf("correlation_id");

            event = new CloudEventBuilder()
                    .withId(uuid)
                    .withType(type)
                    .withSubject(aggregateId)
                    .withExtension(TwbaCloudEvent.CLOUD_EVENT_TENANT_ID, tenantId)
                    .withExtension(TwbaCloudEvent.CLOUD_EVENT_TIMESTAMP, epoch)
                    .withExtension(TwbaCloudEvent.CLOUD_EVENT_PARTITION_KEY, partitionKey)
                    .withExtension(TwbaCloudEvent.CLOUD_EVENT_CORRELATION_ID, correlationId)
                    .withExtension(TwbaCloudEvent.CLOUD_EVENT_GENERATING_APP_NAME, source)
                    .withSource(URI.create("https://thewhiteboardarchitect.com/" + source))
                    .withData("application/json",payload.getBytes("UTF-8"))
                    .build();

            messagePublisher.publish(event);

        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
