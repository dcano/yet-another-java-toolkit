package io.twba.tk.cdc.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.core.JacksonException;
import io.twba.tk.cdc.OutboxMessage;
import io.twba.tk.cdc.OutboxProperties;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageEntity;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageRepositoryJpaHelper;
import io.twba.tk.configure.ToolkitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.twba.tk.cdc.outbox.OutboxJpa.PUBLISHED_COUNTER;
import static io.twba.tk.cdc.outbox.OutboxMessages.randomOutboxMessage;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
public class OutboxJpaTest {

    public static final int NUM_PARTITIONS = 5;
    @Mock
    public OutboxMessageRepositoryJpaHelper helper;

    public OutboxProperties outboxProperties;
    public OutboxJpa outboxJpa;
    @Captor
    ArgumentCaptor<OutboxMessageEntity> outboxEntityCaptor;

    @BeforeEach
    public void setup() {
        outboxProperties = new OutboxProperties();
        outboxProperties.setNumPartitions(NUM_PARTITIONS);
        outboxJpa = new OutboxJpa(outboxProperties, helper);
    }

    @Test
    public void shouldAppendMessageToOutbox() throws JacksonException {
        OutboxMessage expectedMessage = randomOutboxMessage();
        outboxJpa.appendMessage(expectedMessage);
        verify(helper).save(outboxEntityCaptor.capture());
        OutboxMessageEntity actualMessage = outboxEntityCaptor.getValue();
        assertAll("Append outbox message",
                () -> assertEquals(expectedMessage.epoch(), actualMessage.getEpoch()),
                () -> assertEquals(expectedMessage.uuid(), actualMessage.getUuid()),
                () -> assertNotNull(expectedMessage.payload(), actualMessage.getPayload())
        );
    }

    @Test
    public void shouldCountPublishedMessagesWhenInstrumentationEnabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolkitProperties properties = new ToolkitProperties();
        properties.getInstrumentation().setEnabled(true);
        OutboxJpa instrumented = new OutboxJpa(outboxProperties, helper, registry, properties);

        instrumented.appendMessage(randomOutboxMessage());
        instrumented.appendMessage(randomOutboxMessage());

        assertEquals(2.0, registry.get(PUBLISHED_COUNTER).counter().count());
    }

    @Test
    public void shouldNotRegisterCounterWhenInstrumentationDisabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolkitProperties properties = new ToolkitProperties(); // instrumentation disabled by default
        OutboxJpa notInstrumented = new OutboxJpa(outboxProperties, helper, registry, properties);

        notInstrumented.appendMessage(randomOutboxMessage());

        assertNull(registry.find(PUBLISHED_COUNTER).counter());
    }

}
