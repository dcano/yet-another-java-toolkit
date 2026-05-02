package io.twba.tk.cdc;

import java.io.IOException;

public interface MessageRelay extends AutoCloseable{

    void start();
    void stop() throws IOException;
}
