-- Filterzugriffe fuer GET /listings mit stabiler Sortierung nach neuen Anzeigen.
CREATE INDEX idx_listings_owner_created_at ON listings (owner_user_id, created_at DESC);
CREATE INDEX idx_listings_status_created_at ON listings (status, created_at DESC);
CREATE INDEX idx_listings_price_hint ON listings (price_hint);
