-- Eigene Tabelle des User Service. Andere Services speichern nur die user_id
-- als Referenzwert und greifen nicht direkt auf diese Datenbank zu.
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(40) NOT NULL,
    bio TEXT,
    location VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL
);

-- Sortierung nach created_at wird fuer die einfache Listenansicht genutzt.
CREATE INDEX idx_users_created_at ON users (created_at);
