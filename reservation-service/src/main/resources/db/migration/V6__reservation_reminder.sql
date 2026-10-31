-- The expiry time the driver was last reminded about ("your reservation expires in 5 minutes").
-- A redelivered timer message doesn't remind twice; an extended reservation is reminded again.
ALTER TABLE reservation ADD COLUMN reminded_for TIMESTAMP(6) WITH TIME ZONE;
