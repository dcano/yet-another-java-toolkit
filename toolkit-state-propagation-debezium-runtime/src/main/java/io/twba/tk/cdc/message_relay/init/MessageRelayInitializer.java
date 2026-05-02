package io.twba.tk.cdc.message_relay.init;

import io.twba.tk.cdc.MessageRelay;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class MessageRelayInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final MessageRelay messageRelay;

    @Autowired
    public MessageRelayInitializer(MessageRelay messageRelay) {
        this.messageRelay = messageRelay;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        messageRelay.start();
    }
}
