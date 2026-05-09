package io.twba.tk.query;

import java.time.Instant;

public interface DomainQuery<R> {
    String queryUid();
    Instant issuedAt();
    String queryName();
}
