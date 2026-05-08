package com.twba.tk.command.decorator;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.twba.tk.command.CommandHandler;
import io.twba.tk.command.DefaultDomainCommand;
import io.twba.tk.command.decorator.InstrumentationCommandHandlerDecorator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentationCommandHandlerDecoratorTest {

    static class TestCommand extends DefaultDomainCommand {}

    @Test
    void whenEnabled_recordsTimerMetric() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommandHandler<TestCommand> handler = cmd -> {};
        InstrumentationCommandHandlerDecorator decorator =
                new InstrumentationCommandHandlerDecorator(handler, registry, true);

        decorator.handle(new TestCommand());

        Timer timer = registry.find("twba.command.execution")
                .tag("command", "TestCommand")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) > 0);
    }

    @Test
    void whenHandlerThrows_exceptionPropagatesAndTimerStillRecorded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RuntimeException expected = new RuntimeException("fail");
        CommandHandler<TestCommand> handler = cmd -> { throw expected; };
        InstrumentationCommandHandlerDecorator decorator =
                new InstrumentationCommandHandlerDecorator(handler, registry, true);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> decorator.handle(new TestCommand()));

        assertSame(expected, thrown);
        Timer timer = registry.find("twba.command.execution").tag("command", "TestCommand").timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    @Test
    void whenDisabled_noMetricsRecorded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CommandHandler<TestCommand> handler = cmd -> {};
        InstrumentationCommandHandlerDecorator decorator =
                new InstrumentationCommandHandlerDecorator(handler, registry, false);

        decorator.handle(new TestCommand());

        assertNull(registry.find("twba.command.execution").timer());
    }

    @Test
    void whenNullMeterRegistry_noNullPointerException() {
        CommandHandler<TestCommand> handler = cmd -> {};
        InstrumentationCommandHandlerDecorator decorator =
                new InstrumentationCommandHandlerDecorator(handler, null, true);

        assertDoesNotThrow(() -> decorator.handle(new TestCommand()));
    }

    @Test
    void whenConcurrentDispatches_eachRecordedIndependently() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        int threads = 4;
        CountDownLatch latch = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

        CommandHandler<TestCommand> handler = cmd -> {};
        InstrumentationCommandHandlerDecorator decorator =
                new InstrumentationCommandHandlerDecorator(handler, registry, true);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    decorator.handle(new TestCommand());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        pool.shutdown();

        assertTrue(errors.isEmpty(), "No errors expected during concurrent dispatch");
        Timer timer = registry.find("twba.command.execution").tag("command", "TestCommand").timer();
        assertNotNull(timer);
        assertEquals(threads, timer.count());
    }
}
