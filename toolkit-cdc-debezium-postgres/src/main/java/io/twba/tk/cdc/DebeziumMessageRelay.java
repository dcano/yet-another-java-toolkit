package io.twba.tk.cdc;

import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static io.debezium.data.Envelope.FieldName.AFTER;

public class DebeziumMessageRelay implements MessageRelay {

    private static final Logger log = LoggerFactory.getLogger(DebeziumMessageRelay.class);

    private final Executor executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "debezium-message-relay"));
    private final CdcRecordChangeConsumer recordChangeConsumer;
    private final DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine;

    public DebeziumMessageRelay(DebeziumProperties debeziumProperties,
                                CdcRecordChangeConsumer recordChangeConsumer) {

        this(debeziumProperties, recordChangeConsumer, new DebeziumEngine.ConnectorCallback() {
            @Override
            public void connectorStarted() {
                log.info("Debezium connector started");
            }

            @Override
            public void connectorStopped() {
                log.info("Debezium connector stopped");
            }
        });

    }

    public DebeziumMessageRelay(DebeziumProperties debeziumProperties,
                                CdcRecordChangeConsumer recordChangeConsumer,
                                DebeziumEngine.ConnectorCallback connectorCallback) {
        this.debeziumEngine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(DebeziumConfigurationProvider.outboxConnectorConfig(debeziumProperties).asProperties())
                .using(connectorCallback) //add connector callback to control connector state
                .notifying(this::handleChangeEvent)
                .build();
        this.recordChangeConsumer = recordChangeConsumer;
    }

    private void handleChangeEvent(RecordChangeEvent<SourceRecord> sourceRecordRecordChangeEvent)  {
        SourceRecord sourceRecord = sourceRecordRecordChangeEvent.record();
        Struct sourceRecordChangeValue= (Struct) sourceRecord.value();
        log.info("Received record - Key = '{}' value = '{}'", sourceRecord.key(), sourceRecord.value());
        Struct struct = (Struct) sourceRecordChangeValue.get(AFTER);
        recordChangeConsumer.accept(DebeziumCdcRecord.of(struct));
    }

    @Override
    public void start() {
        this.executor.execute(debeziumEngine);
        log.info("Debezium CDC started");
    }

    @Override
    public void stop() throws IOException {
        if (this.debeziumEngine != null) {
            this.debeziumEngine.close();
        }
    }

    @Override
    public void close() throws Exception {
        stop();
    }
}
