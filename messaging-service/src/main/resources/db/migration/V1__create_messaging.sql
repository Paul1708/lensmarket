-- Eine Konversation verbindet eine Anzeige, eine fotografierende Person
-- und eine interessierte Kundin bzw. einen interessierten Kunden.
CREATE TABLE conversations (
    id VARCHAR(36) PRIMARY KEY,
    listing_id VARCHAR(36) NOT NULL,
    photographer_user_id VARCHAR(36) NOT NULL,
    customer_user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Nachrichten sind getrennt von Konversationen gespeichert, damit beliebig viele
-- Nachrichten zu einem Thread gehoeren koennen.
CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_user_id VARCHAR(36) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Indizes fuer Listenansichten und das Laden aller Nachrichten einer Konversation.
CREATE INDEX idx_conversations_created_at ON conversations (created_at);
CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
CREATE INDEX idx_messages_created_at ON messages (created_at);
