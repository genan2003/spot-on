-- When a reservation stopped holding its spot (cancelled, expired or used for a session).
-- "My parking" shows the newest ended reservation or session; an older receipt must not hide a
-- reservation the driver just cancelled (exercise 14.8).
ALTER TABLE reservation ADD COLUMN ended_at TIMESTAMP(6) WITH TIME ZONE;
-- Expired holds ended when they ran out; for older cancelled ones the time wasn't recorded.
UPDATE reservation SET ended_at = expires_at WHERE status = 'EXPIRED';
