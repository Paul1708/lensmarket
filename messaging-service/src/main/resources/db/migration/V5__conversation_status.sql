ALTER TABLE conversations
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN';

ALTER TABLE conversations
ADD COLUMN deleted_by_photographer_at TIMESTAMPTZ,
ADD COLUMN deleted_by_customer_at TIMESTAMPTZ;
