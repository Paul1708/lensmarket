-- Idempotenz fuer das Senden von Nachrichten: Clients koennen einen Idempotency-Key
-- mitschicken. Bei Netzwerk-Retries entsteht so keine doppelte Nachricht, weil der
-- Unique Index pro Konversation und Sender:in nur einen Eintrag pro Key erlaubt.
ALTER TABLE messages ADD COLUMN idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX uq_messages_idempotency
    ON messages (conversation_id, sender_user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
