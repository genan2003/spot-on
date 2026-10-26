-- parking_db: locations, their pricing rule, and spots.
-- Driver ids are plain strings ("driver-1"): there is no user service, the id comes from the request.

CREATE TABLE parking_location (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(100)     NOT NULL,
    address            VARCHAR(200)     NOT NULL,
    latitude           DOUBLE PRECISION NOT NULL,
    longitude          DOUBLE PRECISION NOT NULL,
    -- Pricing rule (Strategy): one per location. strategy_type picks the PricingStrategy,
    -- the rate columns it needs are filled, the others stay NULL.
    strategy_type      VARCHAR(20)      NOT NULL,
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
    -- Spot lifecycle (State): AVAILABLE, RESERVED, OCCUPIED, OUT_OF_SERVICE.
    status      VARCHAR(20) NOT NULL,
    -- Driver holding the spot while RESERVED or OCCUPIED, NULL otherwise.
    held_by     VARCHAR(64),
    -- Optimistic locking: every update checks and increments it (@Version).
    version     BIGINT      NOT NULL,
    UNIQUE (location_id, level, code)
);
CREATE INDEX idx_parking_spot_location ON parking_spot (location_id);

-- Idempotent consumers: the id of every RabbitMQ event already handled. A redelivered event
-- hits the primary key and is skipped.
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
