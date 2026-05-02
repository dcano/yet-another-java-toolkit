CREATE SCHEMA IF NOT EXISTS db_migrations;
CREATE SCHEMA IF NOT EXISTS event_sourcing_schema;

create table event_sourcing_schema.event_store
(
    uuid                 varchar(255)  not null, -- Event unique ID
    aggregate_type       varchar(1000) not null, -- Type of the aggregate root
    aggregate_id         varchar(100)  not null, -- ID of the aggregate instance
    type                 varchar(1000) not null, -- The event type, usually the FQCN of the payload
    payload              jsonb         not null, -- The event payload
    tenant_id            varchar(255),
    event_epoch          int8          not null, -- Timestamp of event creation
    system_epoch         int8          not null,
    event_stream_version int8          not null, -- The version of the event within the aggregate's stream
    event_class_name     varchar(1000),
    primary key (uuid)
);

-- Index for efficient retrieval of an aggregate's event stream
create index event_store_aggregate_stream_idx on event_sourcing_schema.event_store (aggregate_type, aggregate_id, event_stream_version);