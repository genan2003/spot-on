-- The car's licence plate, given by the driver when the session starts (NULL for sessions from before).
-- Sent on to parking-service with occupy, so the admin sees which car is on an occupied bay.
ALTER TABLE parking_session ADD COLUMN licence_plate VARCHAR(12);
