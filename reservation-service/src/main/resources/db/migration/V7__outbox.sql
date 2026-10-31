-- Transactional outbox: every event is saved here in the same transaction as the change it describes,
-- then OutboxRelay sends it to RabbitMQ and sets sent_at. A crash between the commit and the send no
-- longer loses the event; at worst it is sent twice, and consumers ignore duplicates by event id.
CREATE TABLE outbox_event (
    id          UUID PRIMARY KEY,               -- the event's eventId
    routing_key VARCHAR(64)  NOT NULL,           -- e.g. reservation.created
    payload     TEXT         NOT NULL,           -- the event as JSON, exactly as consumers receive it
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    sent_at     TIMESTAMP(6) WITH TIME ZONE
);
-- The relay only looks at unsent rows, oldest first.
CREATE INDEX ix_outbox_event_unsent ON outbox_event (created_at) WHERE sent_at IS NULL;
