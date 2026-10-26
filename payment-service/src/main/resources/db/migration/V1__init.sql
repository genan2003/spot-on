-- payment_db: simulated payments, one per completed parking session.
-- session_id points into reservation-service's database, so it has no foreign key.

CREATE TABLE payment (
    id           UUID PRIMARY KEY,
    -- One payment per session; a retry updates the same row instead of adding one.
    session_id   UUID           NOT NULL UNIQUE,
    driver_id    VARCHAR(64)    NOT NULL,
    amount       NUMERIC(10, 2) NOT NULL,
    -- CARD, WALLET (PaymentProcessorFactory picks the processor)
    method       VARCHAR(20)    NOT NULL,
    -- PENDING, COMPLETED, FAILED
    status       VARCHAR(20)    NOT NULL,
    attempts     INT            NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE
);
CREATE INDEX idx_payment_driver ON payment (driver_id);

-- Idempotent consumers: the id of every RabbitMQ event already handled, so a redelivered
-- session.completed never charges twice.
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
