package io.twba.tk.command.decorator;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.twba.tk.command.CommandHandler;
import io.twba.tk.command.DomainCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class InstrumentationCommandHandlerDecorator implements CommandHandler<DomainCommand> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentationCommandHandlerDecorator.class);

    @SuppressWarnings("rawtypes")
    private final CommandHandler delegate;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;

    public InstrumentationCommandHandlerDecorator(CommandHandler<? extends DomainCommand> delegate,
                                                   MeterRegistry meterRegistry,
                                                   boolean enabled) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(DomainCommand command) {
        if (!enabled) {
            delegate.handle(command);
            return;
        }
        long start = System.nanoTime();
        try {
            delegate.handle(command);
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            if (meterRegistry != null) {
                Timer.builder("twba.command.execution")
                        .tag("command", command.getClass().getSimpleName())
                        .publishPercentileHistogram(true)
                        .register(meterRegistry)
                        .record(elapsedNanos, TimeUnit.NANOSECONDS);
            }
            LOGGER.debug("Command {} executed in {} ms",
                    command.getClass().getSimpleName(),
                    TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
        }
    }
}
