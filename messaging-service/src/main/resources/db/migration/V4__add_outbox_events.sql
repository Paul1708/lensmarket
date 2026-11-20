-- Nachricht und message-sent-Event werden in derselben lokalen Transaktion gespeichert.
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

CREATE INDEX idx_messaging_outbox_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
