-- Pricing rules get more options (exercise 14.2):
--  * tiered pricing has any number of bands instead of a first-hour and a later-hour rate,
--  * night/weekend pricing has configurable night hours,
--  * extras for every strategy, applied as decorators in reservation-service: billing blocks,
--    a free grace period, a daily maximum, and surcharges per bay type and for EV charging.

CREATE TABLE pricing_band (
    location_id UUID          NOT NULL REFERENCES parking_location (id) ON DELETE CASCADE,
    from_hour   INT           NOT NULL,
    hourly_rate NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (location_id, from_hour)
);

-- Existing tiered rules become two bands: the first hour, then every later hour.
INSERT INTO pricing_band (location_id, from_hour, hourly_rate)
SELECT id, 0, first_hour_rate FROM parking_location WHERE strategy_type = 'TIERED'
UNION ALL
SELECT id, 1, later_hour_rate FROM parking_location WHERE strategy_type = 'TIERED';

ALTER TABLE parking_location DROP COLUMN first_hour_rate;
ALTER TABLE parking_location DROP COLUMN later_hour_rate;

ALTER TABLE parking_location ADD COLUMN night_start TIME;
ALTER TABLE parking_location ADD COLUMN night_end TIME;
UPDATE parking_location SET night_start = '20:00', night_end = '08:00' WHERE strategy_type = 'NIGHT_WEEKEND';

-- 60 = every started hour, as before.
ALTER TABLE parking_location ADD COLUMN billing_increment_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE parking_location ADD COLUMN grace_period_minutes INT NOT NULL DEFAULT 0;
-- NULL = no daily maximum.
ALTER TABLE parking_location ADD COLUMN daily_cap NUMERIC(10, 2);
-- Percent added to the price (negative = discount).
ALTER TABLE parking_location ADD COLUMN compact_surcharge INT NOT NULL DEFAULT 0;
ALTER TABLE parking_location ADD COLUMN large_surcharge INT NOT NULL DEFAULT 0;
ALTER TABLE parking_location ADD COLUMN motorcycle_surcharge INT NOT NULL DEFAULT 0;
ALTER TABLE parking_location ADD COLUMN ev_charging_surcharge INT NOT NULL DEFAULT 0;
