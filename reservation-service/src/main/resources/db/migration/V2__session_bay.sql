-- The bay's type and charger, copied from parking-service when the session starts:
-- the location's pricing rule can add a surcharge (or discount) for them (Decorator pattern).
ALTER TABLE parking_session ADD COLUMN spot_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE parking_session ADD COLUMN ev_charging BOOLEAN NOT NULL DEFAULT FALSE;
