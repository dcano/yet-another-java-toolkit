package io.twba.tk.cdc;

import io.cloudevents.CloudEvent;

public interface MessagePublisher {

    boolean publish(CloudEvent dispatchedMessage);

}
