ALTER TABLE idempotency_keys
    ALTER COLUMN reservation_id DROP NOT NULL;

ALTER TABLE idempotency_keys
    ALTER COLUMN response_status DROP NOT NULL;
