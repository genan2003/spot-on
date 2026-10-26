-- notification_db: every (simulated) notification sent to a driver.

CREATE TABLE notification (
    id           UUID PRIMARY KEY,
    driver_id    VARCHAR(64)  NOT NULL,
    -- RESERVATION_CREATED, RESERVATION_CANCELLED, RESERVATION_EXPIRED, SESSION_COMPLETED,
    -- PAYMENT_COMPLETED, PAYMENT_FAILED
    kind         VARCHAR(30)  NOT NULL,
    -- EMAIL, SMS, PUSH (NotificationChannelFactory picks the channel)
    channel_type VARCHAR(20)  NOT NULL,
    message      VARCHAR(500) NOT NULL,
    sent_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notification_driver_sent ON notification (driver_id, sent_at DESC);

-- Idempotent consumers: the id of every RabbitMQ event already handled, so a redelivered
-- event never notifies twice.
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
