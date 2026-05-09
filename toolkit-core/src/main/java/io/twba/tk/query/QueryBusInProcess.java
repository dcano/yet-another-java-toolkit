package io.twba.tk.query;

import io.micrometer.core.instrument.MeterRegistry;
import io.twba.tk.configure.ToolkitProperties;
import io.twba.tk.query.decorator.InstrumentationQueryHandlerDecorator;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryBusInProcess implements QueryBus {

    @SuppressWarnings("rawtypes")
    private final Map<String, QueryHandler> handlersMap;

    public QueryBusInProcess(List<QueryHandler<?, ?>> queryHandlers) {
        this(queryHandlers, new ToolkitProperties(), null);
    }

    public QueryBusInProcess(List<QueryHandler<?, ?>> queryHandlers,
                             ToolkitProperties properties,
                             MeterRegistry meterRegistry) {
        handlersMap = new HashMap<>();
        if (queryHandlers != null) {
            queryHandlers.forEach(handler -> {
                String key = handler.handles();
                if (handlersMap.containsKey(key)) {
                    throw new IllegalStateException(
                            "Duplicate query handler detected for query type: " + key
                            + ". Each query type must have exactly one handler.");
                }
                handlersMap.put(key, decorate(handler, properties, meterRegistry));
            });
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <Q extends DomainQuery<R>, R> Flux<R> dispatch(Q query) {
        String key = query.getClass().getName();
        QueryHandler handler = handlersMap.get(key);
        if (handler == null) {
            throw new IllegalArgumentException(
                    "No query handler registered for: " + key);
        }
        return handler.handle(query);
    }

    @SuppressWarnings("unchecked")
    private QueryHandler<DomainQuery<Object>, Object> decorate(QueryHandler<?, ?> handler,
                                                                ToolkitProperties properties,
                                                                MeterRegistry meterRegistry) {
        boolean instrumentationEnabled = properties.getInstrumentation() != null
                && properties.getInstrumentation().isEnabled();
        QueryHandler<DomainQuery<Object>, Object> cast = (QueryHandler<DomainQuery<Object>, Object>) handler;
        return new InstrumentationQueryHandlerDecorator(cast, meterRegistry, instrumentationEnabled);
    }
}
