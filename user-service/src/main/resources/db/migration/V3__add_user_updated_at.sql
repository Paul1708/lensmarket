-- Profiländerungen sollen nachvollziehbar sein. Bestehende Nutzer starten mit created_at.
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE users
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX idx_users_updated_at ON users (updated_at);
