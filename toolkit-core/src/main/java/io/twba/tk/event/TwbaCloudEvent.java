package io.twba.tk.event;

import io.cloudevents.CloudEvent;
import lombok.Getter;

@Getter
public class TwbaCloudEvent {

    public static final String CLOUD_EVENT_AMQP_BINDING_PREFIX = "cloudEvents_";
    public static final String CLOUD_EVENT_TENANT_ID = "tenantid";
    public static final String CLOUD_EVENT_PARTITION_KEY = "partitionkey";
    public static final String CLOUD_EVENT_TIMESTAMP = "eventepochtimestamp";
    public static final String CLOUD_EVENT_SUBJECT = "subject";
    public static final String CLOUD_EVENT_CORRELATION_ID = "correlationid";
    public static final String CLOUD_EVENT_GENERATING_APP_NAME = "appname";
    public static final String CLOUD_EVENT_SOURCE = "source";

    private final CloudEvent cloudEvent;

    private TwbaCloudEvent(CloudEvent cloudEvent) {
        this.cloudEvent = cloudEvent;
    }

    public static TwbaCloudEvent from(CloudEvent cloudEvent) {
        return new TwbaCloudEvent(cloudEvent);
    }

}
