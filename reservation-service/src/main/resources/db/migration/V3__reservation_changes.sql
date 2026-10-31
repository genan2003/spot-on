-- Drivers can move a reservation to another spot or extend it once (exercise 14.3).
-- version: optimistic locking, so a move or extension and the expiry timer can't overwrite each other.
ALTER TABLE reservation ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE reservation ADD COLUMN extended BOOLEAN NOT NULL DEFAULT FALSE;
