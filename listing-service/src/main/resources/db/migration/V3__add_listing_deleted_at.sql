-- Soft-Delete fuer Anzeigen: historische Referenzen aus Bildern, Nachrichten und Events bleiben erhalten.
ALTER TABLE listings ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_listings_deleted_at ON listings (deleted_at);
