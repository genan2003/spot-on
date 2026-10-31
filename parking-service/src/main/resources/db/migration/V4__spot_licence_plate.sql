-- The licence plate of the car parked on the spot, given by the driver at session start (NULL unless occupied).
-- Shown to the admin on occupied bays; cleared when the spot is freed.
ALTER TABLE parking_spot ADD COLUMN licence_plate VARCHAR(12);
