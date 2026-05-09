package io.twba.tk.query;

import reactor.core.publisher.Flux;

public interface QueryBus {
    <Q extends DomainQuery<R>, R> Flux<R> dispatch(Q query);
}
