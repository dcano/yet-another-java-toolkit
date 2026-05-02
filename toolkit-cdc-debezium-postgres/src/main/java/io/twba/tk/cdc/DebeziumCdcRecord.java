package io.twba.tk.cdc;

import org.apache.kafka.connect.data.Struct;

public class DebeziumCdcRecord implements CdcRecord {

    private final Struct cdcRecord;

    private DebeziumCdcRecord(Struct cdcRecord) {
        this.cdcRecord = cdcRecord;
    }

    public static DebeziumCdcRecord of(Struct raw) {
        return new DebeziumCdcRecord(raw);
    }

    @Override
    public <T> T valueOf(String key) {
        return (T)cdcRecord.get(key);
    }
}
