-- reservation_db: reservations and parking sessions.
-- spot_id and location_id point into parking-service's database, so they have no foreign keys.
-- spot_code and location_name are copies taken when the row is created, so responses don't
-- need a call to parking-service.

CREATE TABLE reservation (
    id            UUID PRIMARY KEY,
    driver_id     VARCHAR(64)  NOT NULL,
    spot_id       UUID         NOT NULL,
    spot_code     VARCHAR(10)  NOT NULL,
    location_id   UUID         NOT NULL,
    location_name VARCHAR(100) NOT NULL,
    -- PENDING, ACTIVE, CANCELLED, EXPIRED, COMPLETED
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
-- No double reservations, enforced by the database: at most one open reservation per spot.
-- A second insert for the same spot fails, and the service answers 409 Conflict.
CREATE UNIQUE INDEX uq_reservation_open_per_spot ON reservation (spot_id)
    WHERE status IN ('PENDING', 'ACTIVE');
-- A driver holds at most one open reservation.
CREATE UNIQUE INDEX uq_reservation_open_per_driver ON reservation (driver_id)
    WHERE status IN ('PENDING', 'ACTIVE');
-- The expiration sweep looks for open reservations past expires_at.
CREATE INDEX idx_reservation_expires_at ON reservation (expires_at)
    WHERE status IN ('PENDING', 'ACTIVE');

CREATE TABLE parking_session (
    id             UUID PRIMARY KEY,
    driver_id      VARCHAR(64)  NOT NULL,
    spot_id        UUID         NOT NULL,
    spot_code      VARCHAR(10)  NOT NULL,
    location_id    UUID         NOT NULL,
    location_name  VARCHAR(100) NOT NULL,
    -- The reservation this session used, NULL for a drive-in without one.
    reservation_id UUID UNIQUE REFERENCES reservation (id),
    -- ACTIVE, COMPLETED
    status         VARCHAR(20)  NOT NULL,
    started_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    ended_at       TIMESTAMP(6) WITH TIME ZONE,
    -- Set when the session ends: pricing strategy applied to (ended_at - started_at).
    cost           NUMERIC(10, 2),
    -- Filled from payment.completed / payment.failed events.
    payment_id     UUID,
    payment_status VARCHAR(20)
);
-- One car per spot and one running session per driver.
CREATE UNIQUE INDEX uq_session_active_per_spot ON parking_session (spot_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_session_active_per_driver ON parking_session (driver_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_session_driver ON parking_session (driver_id);

-- Idempotent consumers: the id of every RabbitMQ event already handled.
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
