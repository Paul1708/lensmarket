-- Passwort-Hashes bleiben im User Service. Bestehende Demo-Nutzer haben zunächst keinen Hash.
ALTER TABLE users ADD COLUMN password_hash TEXT;
