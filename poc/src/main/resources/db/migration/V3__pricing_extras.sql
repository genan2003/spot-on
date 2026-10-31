-- More pricing options (Decorator pattern): tiered bands, configurable night hours,
-- and extras for every strategy (billing blocks, grace period, daily cap, spot surcharges).

CREATE TABLE pricing_band (
    location_id UUID           NOT NULL REFERENCES parking_location (id) ON DELETE CASCADE,
    from_hour   INT            NOT NULL,
    hourly_rate NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (location_id, from_hour)
);

INSERT INTO pricing_band (location_id, from_hour, hourly_rate)
SELECT id, 0, first_hour_rate FROM parking_location WHERE strategy_type = 'TIERED';
INSERT INTO pricing_band (location_id, from_hour, hourly_rate)
SELECT id, 1, later_hour_rate FROM parking_location WHERE strategy_type = 'TIERED';

ALTER TABLE parking_location DROP COLUMN first_hour_rate;
ALTER TABLE parking_location DROP COLUMN later_hour_rate;

ALTER TABLE parking_location ADD COLUMN night_start TIME;
ALTER TABLE parking_location ADD COLUMN night_end TIME;
UPDATE parking_location SET night_start = '20:00:00', night_end = '08:00:00' WHERE strategy_type = 'NIGHT_WEEKEND';

ALTER TABLE parking_location ADD COLUMN billing_increment_minutes INT DEFAULT 60 NOT NULL;
ALTER TABLE parking_location ADD COLUMN grace_period_minutes INT DEFAULT 0 NOT NULL;
ALTER TABLE parking_location ADD COLUMN daily_cap NUMERIC(10, 2);
ALTER TABLE parking_location ADD COLUMN compact_surcharge INT DEFAULT 0 NOT NULL;
ALTER TABLE parking_location ADD COLUMN large_surcharge INT DEFAULT 0 NOT NULL;
ALTER TABLE parking_location ADD COLUMN motorcycle_surcharge INT DEFAULT 0 NOT NULL;
ALTER TABLE parking_location ADD COLUMN ev_charging_surcharge INT DEFAULT 0 NOT NULL;
