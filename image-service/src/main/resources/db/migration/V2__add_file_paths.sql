-- Dateipfade fuer echten Upload. Beide Spalten sind nullable,
-- damit bestehende Metadaten-Eintraege (MVP-Weg) gueltig bleiben.
ALTER TABLE images
    ADD COLUMN original_path VARCHAR(512),
    ADD COLUMN watermarked_path VARCHAR(512);
