package io.twba.tk.aspects;

import io.twba.tk.core.DomainEventAppender;
import io.twba.tk.core.Entity;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@Named
public class DomainEventAppenderConcern {

    private final DomainEventAppender domainEventAppender;

    @Inject
    public DomainEventAppenderConcern(DomainEventAppender domainEventAppender) {
        this.domainEventAppender = domainEventAppender;
    }

    @After(value = "io.twba.tk.aspects.CrossPointcuts.shouldAppendEvents()")
    public void appendEventsToBuffer(JoinPoint jp) {
        if(Entity.class.isAssignableFrom(jp.getArgs()[0].getClass())) {
            Entity entity = (Entity)jp.getArgs()[0];
            domainEventAppender.append(entity.getDomainEvents());
        }
    }


}
