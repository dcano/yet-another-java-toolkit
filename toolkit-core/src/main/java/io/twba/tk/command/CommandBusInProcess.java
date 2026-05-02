package io.twba.tk.command;

import io.twba.tk.command.decorator.PublishBufferedEventsCommandHandlerDecorator;
import io.twba.tk.command.decorator.TransactionalCommandHandlerDecorator;
import io.twba.tk.core.DomainEventAppender;
import io.twba.tk.core.TwbaTransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandBusInProcess implements CommandBus {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandBusInProcess.class);

    private final Map<String, CommandHandler<DomainCommand>> handlersMap;
    private final DomainEventAppender domainEventAppender;

    public CommandBusInProcess(List<CommandHandler<? extends DomainCommand>> commandHandlers,
                               DomainEventAppender domainEventAppender, TwbaTransactionManager transactionManager) {
        this.domainEventAppender = domainEventAppender;
        handlersMap = new HashMap<>();
        if(commandHandlers != null) {
            commandHandlers.forEach(handler -> handlersMap.put(handler.handles(), decorate(handler, transactionManager)));
        }

    }

    public CommandBusInProcess(List<CommandHandler<? extends DomainCommand>> commandHandlers, TwbaTransactionManager transactionManager) {
        domainEventAppender = null;
        handlersMap = new HashMap<>();
        if(commandHandlers != null) {
            commandHandlers.forEach(handler -> handlersMap.put(handler.handles(), new TransactionalCommandHandlerDecorator(handler, transactionManager)));
        }

    }

    @Override
    public <T extends DomainCommand> void push(T command) {
        if(handlersMap.containsKey(command.getClass().getName())) {
            var handler = handlersMap.get(command.getClass().getName());
            handler.handle(command);
        }
    }

    private CommandHandler<DomainCommand> decorate(CommandHandler<? extends  DomainCommand> handler, TwbaTransactionManager transactionManager) {
        return new TransactionalCommandHandlerDecorator(new PublishBufferedEventsCommandHandlerDecorator(handler, domainEventAppender), transactionManager);
    }
}
