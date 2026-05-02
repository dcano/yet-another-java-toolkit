package io.twba.tk.cdc.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.twba.tk.cdc.OutboxMessage;
import io.twba.tk.cdc.OutboxProperties;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageEntity;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageRepositoryJpaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.twba.tk.cdc.outbox.OutboxMessages.randomOutboxMessage;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
public class OutboxJpaTest {

    public static final int NUM_PARTITIONS = 5;
    @Mock
    public OutboxMessageRepositoryJpaHelper helper;

    public OutboxJpa outboxJpa;
    @Captor
    ArgumentCaptor<OutboxMessageEntity> outboxEntityCaptor;

    @BeforeEach
    public void setup() {
        OutboxProperties outboxProperties = new OutboxProperties();
        outboxProperties.setNumPartitions(NUM_PARTITIONS);
        outboxJpa = new OutboxJpa(outboxProperties, helper);
    }

    @Test
    public void shouldAppendMessageToOutbox() throws JsonProcessingException {
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

}
