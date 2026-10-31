-- A car park's opening hours in its local time (Europe/Bucharest). Both NULL: open around the clock.
-- closes_at earlier than opens_at means it closes after midnight (22:00 to 06:00).
-- Reserving and starting a session are refused while it is closed; leaving is always allowed.
ALTER TABLE parking_location ADD COLUMN opens_at TIME;
ALTER TABLE parking_location ADD COLUMN closes_at TIME;
ALTER TABLE parking_location ADD CONSTRAINT ck_location_opening_hours
    CHECK ((opens_at IS NULL AND closes_at IS NULL) OR (opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at <> closes_at));
