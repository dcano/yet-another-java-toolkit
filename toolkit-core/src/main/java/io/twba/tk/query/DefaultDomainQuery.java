package io.twba.tk.query;

import java.time.Instant;
import java.util.UUID;

public abstract class DefaultDomainQuery<R> implements DomainQuery<R> {

    private final String queryUid;
    private final Instant issuedAt;
    private final String queryName;

    protected DefaultDomainQuery() {
        this(UUID.randomUUID().toString(), Instant.now());
    }

    protected DefaultDomainQuery(String queryUid, Instant issuedAt) {
        this.queryUid = queryUid;
        this.issuedAt = issuedAt;
        this.queryName = this.getClass().getName();
    }

    @Override
    public String queryUid() {
        return queryUid;
    }

    @Override
    public Instant issuedAt() {
        return issuedAt;
    }

    @Override
    public String queryName() {
        return queryName;
    }
}
