-- The reservation or session currently holding the spot (NULL while free or closed).
-- Release events name the hold they end, so a late or repeated event can't free a newer hold.
ALTER TABLE parking_spot ADD COLUMN hold_id UUID;
