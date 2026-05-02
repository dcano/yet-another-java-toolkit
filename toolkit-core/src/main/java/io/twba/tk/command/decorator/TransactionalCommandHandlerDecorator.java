package io.twba.tk.command.decorator;

import io.twba.tk.command.CommandHandler;
import io.twba.tk.command.DomainCommand;
import io.twba.tk.core.TwbaTransactionManager;

public class TransactionalCommandHandlerDecorator implements CommandHandler<DomainCommand> {

    @SuppressWarnings("rawtypes")
    private final CommandHandler commandHandler;
    private final TwbaTransactionManager transactionManager;

    public TransactionalCommandHandlerDecorator(CommandHandler<? extends DomainCommand> commandHandler,
                                                TwbaTransactionManager transactionManager) {
        this.commandHandler = commandHandler;
        this.transactionManager = transactionManager;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(DomainCommand command) {
        try {
            transactionManager.begin();
            commandHandler.handle(command);
            transactionManager.commit();
        }
        catch(Exception e) {
            transactionManager.rollback();
            throw e;
        }

    }
}
