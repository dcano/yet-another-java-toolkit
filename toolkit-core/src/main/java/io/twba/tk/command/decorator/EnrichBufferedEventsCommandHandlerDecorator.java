package io.twba.tk.command.decorator;

import io.twba.tk.command.CommandHandler;
import io.twba.tk.command.DomainCommand;
import io.twba.tk.core.DomainEventAppender;

public class EnrichBufferedEventsCommandHandlerDecorator implements CommandHandler<DomainCommand> {

    @SuppressWarnings("rawtypes")
    private final CommandHandler commandHandler;
    private final DomainEventAppender domainEventAppender;

    public EnrichBufferedEventsCommandHandlerDecorator(CommandHandler<? extends DomainCommand> commandHandler,
                                                       DomainEventAppender domainEventAppender) {
        this.commandHandler = commandHandler;
        this.domainEventAppender = domainEventAppender;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(DomainCommand command) {
        commandHandler.handle(command);
        domainEventAppender.enrichForCommand(command);
    }

}
