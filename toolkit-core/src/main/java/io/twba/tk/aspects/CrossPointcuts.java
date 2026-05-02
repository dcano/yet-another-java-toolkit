package io.twba.tk.aspects;

import org.aspectj.lang.annotation.Pointcut;

public class CrossPointcuts {

    @Pointcut("@annotation(io.twba.tk.core.AppendEvents)")
    public void shouldAppendEvents() {}


}
