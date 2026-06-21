package io.twba.tk.query.decorator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.twba.tk.query.DomainQuery;
import io.twba.tk.query.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.concurrent.TimeUnit;

public class InstrumentationQueryHandlerDecorator implements QueryHandler<DomainQuery<Object>, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentationQueryHandlerDecorator.class);

    @SuppressWarnings("rawtypes")
    private final QueryHandler delegate;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;

    public InstrumentationQueryHandlerDecorator(QueryHandler<DomainQuery<Object>, Object> delegate,
                                                MeterRegistry meterRegistry,
                                                boolean enabled) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Flux<Object> handle(DomainQuery<Object> query) {
        if (!enabled) {
            return delegate.handle(query);
        }
        return Flux.defer(() -> {
            long start = System.nanoTime();
            return ((Flux<Object>) delegate.handle(query))
                    .doFinally(signalType -> {
                        long elapsedNanos = System.nanoTime() - start;
                        if (meterRegistry != null) {
                            Timer.builder("twba.query.execution")
                                    .tag("query", query.getClass().getSimpleName())
                                    .publishPercentileHistogram(true)
                                    .register(meterRegistry)
                                    .record(elapsedNanos, TimeUnit.NANOSECONDS);
                        }
                        LOGGER.debug("Query {} completed with signal {} in {} ms",
                                query.getClass().getSimpleName(),
                                signalType,
                                TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
                    });
        });
    }
}
