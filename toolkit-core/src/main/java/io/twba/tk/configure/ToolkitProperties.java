package io.twba.tk.configure;

import lombok.Data;

@Data
public class ToolkitProperties {

    private EventSourcing eventSourcing;
    private Instrumentation instrumentation = new Instrumentation();

    @Data
    public static class EventSourcing {
        // type of the event store to use, valid values are: postgres, inmemory
        private String type;
    }

    @Data
    public static class Instrumentation {
        private boolean enabled = false;
    }

}
