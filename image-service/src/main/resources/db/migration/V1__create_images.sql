-- Eigene Tabelle des Image Service. Im MVP werden hier Metadaten und die
-- Wasserzeichen-URL gespeichert; echte Bilddateien folgen spaeter per Object Storage.
CREATE TABLE images (
    id VARCHAR(36) PRIMARY KEY,
    listing_id VARCHAR(36) NOT NULL,
    owner_user_id VARCHAR(36) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    width INTEGER,
    height INTEGER,
    status VARCHAR(40) NOT NULL,
    watermarked_url VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Listing-Zuordnung und Erstellzeit sind die wichtigsten Lesezugriffe.
CREATE INDEX idx_images_listing_id ON images (listing_id);
CREATE INDEX idx_images_created_at ON images (created_at);
