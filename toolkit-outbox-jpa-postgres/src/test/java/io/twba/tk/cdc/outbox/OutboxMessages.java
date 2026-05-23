package io.twba.tk.cdc.outbox;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import io.twba.tk.cdc.OutboxMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OutboxMessages {

    static OutboxMessage randomOutboxMessage() throws JacksonException {
        return new OutboxMessage(UUID.randomUUID().toString(), "", fakePayload(), "typeA", Instant.now().toEpochMilli(), "partitionA", "tenantTest", "correlationId_1", "test-service", "agregate-id-A");
    }

    static String fakePayload() throws JacksonException {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");

        JsonMapper mapper = JsonMapper.builder().build();
        return mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(map);
    }

}
