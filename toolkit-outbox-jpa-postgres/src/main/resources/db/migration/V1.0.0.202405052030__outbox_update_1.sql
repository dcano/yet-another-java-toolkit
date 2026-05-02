alter table outbox_schema.outbox add column correlation_id varchar(100);
alter table outbox_schema.outbox add column source varchar(500);