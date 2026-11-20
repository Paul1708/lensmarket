-- Eigene Tabelle des Listing Service. Sie enthaelt nur Anzeigen-Daten und
-- bewusst keine Nutzer- oder Bilddetails anderer Services.
CREATE TABLE listings (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id VARCHAR(36) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    price_hint NUMERIC(10, 2),
    location VARCHAR(120),
    camera_model VARCHAR(120),
    lens_model VARCHAR(120),
    tags TEXT NOT NULL,
    image_ids TEXT NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Die Indizes decken die aktuellen MVP-Zugriffe ab: neue Anzeigen zuerst
-- und Anzeigen eines bestimmten Fotografen.
CREATE INDEX idx_listings_created_at ON listings (created_at);
CREATE INDEX idx_listings_owner_user_id ON listings (owner_user_id);
