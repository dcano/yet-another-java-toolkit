package io.twba.tk.cdc;

import lombok.Data;

import java.util.Map;

@Data
public class DebeziumProperties {

    private String connectorClass;
    private OffsetProperties offsetStorage;
    private SourceDatabaseProperties sourceDatabaseProperties;
    private Map<String, String> customProps;

    @Data
    public static class OffsetProperties {
        private String type;
        private long flushInterval;
        private Map<String, String> offsetProps;

    }
    @Data
    public static class SourceDatabaseProperties {
        private String hostname;
        private int port;
        private String user;
        private String password;
        private String dbName;
        private String serverId;
        private String serverName;
        private String outboxSchema;
        private String outboxTable;

    }
}
