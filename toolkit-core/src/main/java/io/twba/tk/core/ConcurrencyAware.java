package io.twba.tk.core;

public interface ConcurrencyAware {

    Long getVersion();
    boolean isStaleWith(ConcurrencyAware existingEntity);


}
