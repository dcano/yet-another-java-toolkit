package com.twba.tk.command;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.twba.tk.command.CommandBusInProcess;
import io.twba.tk.command.CommandHandler;
import io.twba.tk.command.DefaultDomainCommand;
import io.twba.tk.configure.ToolkitProperties;
import io.twba.tk.core.DomainEventAppender;
import io.twba.tk.core.TwbaTransactionManager;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CommandBusInProcessTest {

    @Mock
    public DomainEventAppender domainEventAppender;

    @Mock
    public CommandHandler<MyCommand> commandHandler;

    @Test
    public void shouldExecuteCommands() {
        MyCommand command = new MyCommand("testProp");
        when(commandHandler.handles()).thenReturn(command.commandName());
        CommandBusInProcess commandBusInProcess = new CommandBusInProcess(Collections.singletonList(commandHandler), domainEventAppender, new TwbaTransactionManager() {
            @Override
            public void begin() {

            }

            @Override
            public void commit() {

            }

            @Override
            public void rollback() {

            }
        });
        commandBusInProcess.push(command);
        verify(commandHandler).handle(command);
        verify(domainEventAppender).publishToOutbox();
    }


    @Test
    public void shouldRecordTimerWhenInstrumentationEnabled() {
        MyCommand command = new MyCommand("testProp");
        when(commandHandler.handles()).thenReturn(command.commandName());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolkitProperties properties = new ToolkitProperties();
        properties.getInstrumentation().setEnabled(true);
        CommandBusInProcess commandBusInProcess = new CommandBusInProcess(
                Collections.singletonList(commandHandler), domainEventAppender,
                noopTransactionManager(), properties, registry);

        commandBusInProcess.push(command);

        Timer timer = registry.find("twba.command.execution")
                .tag("command", "MyCommand")
                .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
    }

    private static TwbaTransactionManager noopTransactionManager() {
        return new TwbaTransactionManager() {
            @Override public void begin() {}
            @Override public void commit() {}
            @Override public void rollback() {}
        };
    }

    @Getter
    public static class MyCommand extends DefaultDomainCommand {

        private final String propertyTest;

        public MyCommand(String propertyTest) {
            this.propertyTest = propertyTest;
        }
    }

}
