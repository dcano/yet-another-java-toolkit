package io.twba.tk.cdc;

import io.twba.tk.event.TwbaCloudEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventBuilder;

import java.net.URI;


public class CloudEventRecordChangeConsumer implements CdcRecordChangeConsumer {

    private final MessagePublisher messagePublisher;

    public CloudEventRecordChangeConsumer(MessagePublisher messagePublisher) {
        this.messagePublisher = messagePublisher;
    }

    @Override
    public void accept(CdcRecord cdcRecord) {

        final CloudEvent event;

        try {
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
