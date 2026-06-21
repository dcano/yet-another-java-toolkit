package io.twba.tk.cdc.outbox;

import io.twba.tk.cdc.OutboxCleaner;
import io.twba.tk.cdc.outbox.jpa.OutboxMessageRepositoryJpaHelper;
import org.springframework.stereotype.Component;

@Component
public class OutboxCleanerJpa implements OutboxCleaner {

    private final OutboxMessageRepositoryJpaHelper helper;

    public OutboxCleanerJpa(OutboxMessageRepositoryJpaHelper helper) {
        this.helper = helper;
    }

    @Override
    public void deleteByUuid(String uuid) {
        helper.deleteByUuidJpql(uuid);
    }

}
