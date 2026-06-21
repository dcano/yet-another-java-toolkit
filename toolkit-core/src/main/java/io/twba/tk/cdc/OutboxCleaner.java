package io.twba.tk.cdc;

public interface OutboxCleaner {

    void deleteByUuid(String uuid);

}
