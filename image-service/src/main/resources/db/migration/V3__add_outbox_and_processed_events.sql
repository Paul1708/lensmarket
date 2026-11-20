-- Atomare Domain-Event-Publikation: Bildmetadaten und Events werden gemeinsam committed.
CREATE TABLE outbox_events (
    event_id VARCHAR(36) PRIMARY KEY,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000)
);

CREATE INDEX idx_image_outbox_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

-- Tombstones verhindern, dass ein bereits gelöschtes Listing durch einen verspätet
-- abgeschlossenen Upload wieder Bildmetadaten erhält.
CREATE TABLE deleted_listings (
    listing_id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    deleted_at TIMESTAMPTZ NOT NULL
);
