package io.twba.tk.command;

import io.micrometer.core.instrument.MeterRegistry;
import io.twba.tk.command.decorator.InstrumentationCommandHandlerDecorator;
import io.twba.tk.command.decorator.PublishBufferedEventsCommandHandlerDecorator;
import io.twba.tk.command.decorator.TransactionalCommandHandlerDecorator;
import io.twba.tk.configure.ToolkitProperties;
import io.twba.tk.core.DomainEventAppender;
import io.twba.tk.core.TwbaTransactionManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandBusInProcess implements CommandBus {

    private final Map<String, CommandHandler<DomainCommand>> handlersMap;
    private final DomainEventAppender domainEventAppender;

    public CommandBusInProcess(List<CommandHandler<? extends DomainCommand>> commandHandlers,
                               DomainEventAppender domainEventAppender,
                               TwbaTransactionManager transactionManager) {
        this(commandHandlers, domainEventAppender, transactionManager, new ToolkitProperties(), null);
    }

    public CommandBusInProcess(List<CommandHandler<? extends DomainCommand>> commandHandlers,
                               TwbaTransactionManager transactionManager) {
        this(commandHandlers, null, transactionManager, new ToolkitProperties(), null);
    }

    public CommandBusInProcess(List<CommandHandler<? extends DomainCommand>> commandHandlers,
                               DomainEventAppender domainEventAppender,
                               TwbaTransactionManager transactionManager,
                               ToolkitProperties properties,
                               MeterRegistry meterRegistry) {
        this.domainEventAppender = domainEventAppender;
        handlersMap = new HashMap<>();
        if (commandHandlers != null) {
            commandHandlers.forEach(handler ->
                    handlersMap.put(handler.handles(), decorate(handler, transactionManager, properties, meterRegistry)));
        }
    }

    @Override
    public <T extends DomainCommand> void push(T command) {
        if (handlersMap.containsKey(command.getClass().getName())) {
            var handler = handlersMap.get(command.getClass().getName());
            handler.handle(command);
        }
    }

    private CommandHandler<DomainCommand> decorate(CommandHandler<? extends DomainCommand> handler,
                                                    TwbaTransactionManager transactionManager,
                                                    ToolkitProperties properties,
                                                    MeterRegistry meterRegistry) {
        CommandHandler<DomainCommand> chain;
        if (domainEventAppender != null) {
            chain = new TransactionalCommandHandlerDecorator(
                    new PublishBufferedEventsCommandHandlerDecorator(handler, domainEventAppender),
                    transactionManager);
        } else {
            chain = new TransactionalCommandHandlerDecorator(handler, transactionManager);
        }
        boolean instrumentationEnabled = properties.getInstrumentation() != null
                && properties.getInstrumentation().isEnabled();
        return new InstrumentationCommandHandlerDecorator(chain, meterRegistry, instrumentationEnabled);
    }
}
