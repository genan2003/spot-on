-- Why a reservation was cancelled: DRIVER, or SPOT_CLOSED when an admin took the spot out of service.
ALTER TABLE reservation ADD COLUMN cancel_reason VARCHAR(20);
UPDATE reservation SET cancel_reason = 'DRIVER' WHERE status = 'CANCELLED';
