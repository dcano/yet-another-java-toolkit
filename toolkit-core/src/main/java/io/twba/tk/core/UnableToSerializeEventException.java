package io.twba.tk.core;

public class UnableToSerializeEventException extends RuntimeException {

    public UnableToSerializeEventException(Class<?> eventType, Exception cause) {
        super("Unable to serialize event of type " + eventType.getName(), cause);
    }


}
