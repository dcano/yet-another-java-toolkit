package com.twba.tk.query;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.twba.tk.configure.ToolkitProperties;
import io.twba.tk.query.DefaultDomainQuery;
import io.twba.tk.query.QueryBusInProcess;
import io.twba.tk.query.QueryHandler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryBusInProcessTest {

    static class MyQuery extends DefaultDomainQuery<String> {}
    static class AnotherQuery extends DefaultDomainQuery<String> {}

    static class MyQueryHandler implements QueryHandler<MyQuery, String> {
        @Override
        public Flux<String> handle(MyQuery query) {
            return Flux.just("result");
        }
    }

    static class DuplicateMyQueryHandler implements QueryHandler<MyQuery, String> {
        @Override
        public Flux<String> handle(MyQuery query) {
            return Flux.empty();
        }
    }

    @Test
    void shouldDispatchQueryAndReturnResult() {
        QueryBusInProcess bus = new QueryBusInProcess(List.of(new MyQueryHandler()));

        Flux<String> result = bus.dispatch(new MyQuery());

        assertEquals(List.of("result"), result.collectList().block());
    }

    @Test
    void shouldThrowWhenNoHandlerRegistered() {
        QueryBusInProcess bus = new QueryBusInProcess(List.of());

        assertThrows(IllegalArgumentException.class, () -> bus.dispatch(new MyQuery()));
    }

    @Test
    void shouldThrowOnDuplicateHandlerAtStartup() {
        assertThrows(IllegalStateException.class, () ->
                new QueryBusInProcess(List.of(new MyQueryHandler(), new DuplicateMyQueryHandler())));
    }

    @Test
    void shouldRecordTimerWhenInstrumentationEnabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolkitProperties properties = new ToolkitProperties();
        properties.getInstrumentation().setEnabled(true);

        QueryBusInProcess bus = new QueryBusInProcess(List.of(new MyQueryHandler()), properties, registry);
        bus.dispatch(new MyQuery()).blockLast();

        Timer timer = registry.find("twba.query.execution")
                .tag("query", "MyQuery")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void shouldNotRecordTimerWhenInstrumentationDisabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolkitProperties properties = new ToolkitProperties();
        properties.getInstrumentation().setEnabled(false);

        QueryBusInProcess bus = new QueryBusInProcess(List.of(new MyQueryHandler()), properties, registry);
        bus.dispatch(new MyQuery()).blockLast();

        Timer timer = registry.find("twba.query.execution")
                .tag("query", "MyQuery")
                .timer();
        assertNull(timer);
    }
}
