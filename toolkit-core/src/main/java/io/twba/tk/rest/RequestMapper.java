package io.twba.tk.rest;

import io.twba.tk.command.DomainCommand;

public interface RequestMapper<REQUEST> {

    boolean maps(Class<?> requestClass);
    DomainCommand toCommand(REQUEST request);

}
