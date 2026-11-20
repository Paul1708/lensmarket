-- Konversationen bekommen eine eigene Aktivitätszeit, damit Chatlisten nach
-- der letzten Nachricht sortiert werden können.
ALTER TABLE conversations ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE conversations
SET updated_at = GREATEST(
    created_at,
    COALESCE(
        (
            SELECT MAX(messages.created_at)
            FROM messages
            WHERE messages.conversation_id = conversations.id
        ),
        created_at
    )
);

ALTER TABLE conversations ALTER COLUMN updated_at SET NOT NULL;

-- Alte Entwicklungsdaten konnten mehrere Threads je Anzeige und Kund:in haben.
-- Vor dem Unique Index werden diese Duplikate zusammengeführt.
WITH duplicate_conversations AS (
    SELECT
        id,
        FIRST_VALUE(id) OVER (
            PARTITION BY listing_id, customer_user_id
            ORDER BY created_at ASC, id ASC
        ) AS keep_id,
        ROW_NUMBER() OVER (
            PARTITION BY listing_id, customer_user_id
            ORDER BY created_at ASC, id ASC
        ) AS duplicate_rank
    FROM conversations
)
UPDATE messages
SET conversation_id = duplicate_conversations.keep_id
FROM duplicate_conversations
WHERE messages.conversation_id = duplicate_conversations.id
  AND duplicate_conversations.duplicate_rank > 1;

WITH conversation_activity AS (
    SELECT
        conversations.id,
        GREATEST(conversations.created_at, COALESCE(MAX(messages.created_at), conversations.created_at)) AS updated_at
    FROM conversations
    LEFT JOIN messages ON messages.conversation_id = conversations.id
    GROUP BY conversations.id, conversations.created_at
)
UPDATE conversations
SET updated_at = conversation_activity.updated_at
FROM conversation_activity
WHERE conversations.id = conversation_activity.id;

WITH duplicate_conversations AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY listing_id, customer_user_id
            ORDER BY created_at ASC, id ASC
        ) AS duplicate_rank
    FROM conversations
)
DELETE FROM conversations
USING duplicate_conversations
WHERE conversations.id = duplicate_conversations.id
  AND duplicate_conversations.duplicate_rank > 1;

-- Ein Kunde kann pro Anzeige genau einen Chat mit dem Fotografen haben.
CREATE UNIQUE INDEX idx_conversations_listing_customer_unique
    ON conversations (listing_id, customer_user_id);

CREATE INDEX idx_conversations_listing_id ON conversations (listing_id);
CREATE INDEX idx_conversations_photographer_updated_at
    ON conversations (photographer_user_id, updated_at DESC);
CREATE INDEX idx_conversations_customer_updated_at
    ON conversations (customer_user_id, updated_at DESC);
