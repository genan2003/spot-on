-- Schema for the proof of concept (H2). Column names follow Hibernate's default naming.

CREATE TABLE users (
    id                       UUID PRIMARY KEY,
    user_type                VARCHAR(31)  NOT NULL,
    name                     VARCHAR(100) NOT NULL,
    email                    VARCHAR(200) NOT NULL,
    phone                    VARCHAR(30),
    preferred_channel        VARCHAR(20),
    preferred_payment_method VARCHAR(20)
);

CREATE TABLE parking_location (
    id                 UUID PRIMARY KEY,
    admin_id           UUID REFERENCES users (id),
    name               VARCHAR(100) NOT NULL,
    address            VARCHAR(200) NOT NULL,
    latitude           DOUBLE PRECISION NOT NULL,
    longitude          DOUBLE PRECISION NOT NULL,
    strategy_type      VARCHAR(20) NOT NULL,
    hourly_rate        NUMERIC(10, 2),
    flat_rate          NUMERIC(10, 2),
    first_hour_rate    NUMERIC(10, 2),
    later_hour_rate    NUMERIC(10, 2),
    day_rate           NUMERIC(10, 2),
    night_weekend_rate NUMERIC(10, 2)
);

CREATE TABLE parking_spot (
    id          UUID PRIMARY KEY,
    location_id UUID        NOT NULL REFERENCES parking_location (id),
    code        VARCHAR(10) NOT NULL,
    level       INT         NOT NULL,
    zone        VARCHAR(5)  NOT NULL,
    type        VARCHAR(20) NOT NULL,
    ev_charging BOOLEAN     NOT NULL,
    accessible  BOOLEAN     NOT NULL,
    status      VARCHAR(20) NOT NULL,
    -- Optimistic locking: every update checks and increments it (@Version).
    version     BIGINT      NOT NULL,
    UNIQUE (location_id, level, code)
);

CREATE TABLE reservation (
    id         UUID PRIMARY KEY,
    driver_id  UUID        NOT NULL REFERENCES users (id),
    spot_id    UUID        NOT NULL REFERENCES parking_spot (id),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- No double reservations, enforced by the database: at most one ACTIVE reservation per spot.
    -- PostgreSQL does this with a partial index (UNIQUE (spot_id) WHERE status = 'ACTIVE').
    -- H2 has no partial indexes, so this column holds spot_id only while the reservation is
    -- ACTIVE (NULL otherwise) and gets a unique index; NULLs don't clash.
    active_spot_id UUID GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN spot_id END)
);
CREATE UNIQUE INDEX uq_reservation_one_active_per_spot ON reservation (active_spot_id);

CREATE TABLE parking_session (
    id             UUID PRIMARY KEY,
    driver_id      UUID        NOT NULL REFERENCES users (id),
    spot_id        UUID        NOT NULL REFERENCES parking_spot (id),
    reservation_id UUID UNIQUE REFERENCES reservation (id),
    entry_time     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    exit_time      TIMESTAMP(6) WITH TIME ZONE,
    cost           NUMERIC(10, 2),
    status         VARCHAR(20) NOT NULL
);

CREATE TABLE payment (
    id           UUID PRIMARY KEY,
    session_id   UUID           NOT NULL REFERENCES parking_session (id),
    amount       NUMERIC(10, 2) NOT NULL,
    method       VARCHAR(20)    NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    processed_at TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE notification (
    id           UUID PRIMARY KEY,
    recipient_id UUID         NOT NULL REFERENCES users (id),
    channel_type VARCHAR(20)  NOT NULL,
    message      VARCHAR(500) NOT NULL,
    sent_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
