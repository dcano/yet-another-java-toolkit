package io.twba.tk.cdc;

import lombok.Data;

@Data
public class MessageRelayProps {

    private String serviceName;

    /**
     * When true the publisher enables mandatory delivery and a returns callback so that
     * unroutable messages are logged at ERROR level. Requires publisher returns to be
     * enabled on the connection factory (see DebeziumConfiguration). Defaults to false.
     */
    private boolean publisherReturns = false;

}
