DELETE FROM reservation_seats
WHERE reservation_id IN (
    SELECT id
    FROM reservations
    WHERE show_id = '550e8400-e29b-41d4-a716-446655440000'
);

DELETE FROM reservations
WHERE show_id = '550e8400-e29b-41d4-a716-446655440000';

DELETE FROM idempotency_keys
WHERE show_id = '550e8400-e29b-41d4-a716-446655440000';

DELETE FROM user_show
WHERE show_id = '550e8400-e29b-41d4-a716-446655440000';

UPDATE seats
SET status = 'AVAILABLE'
WHERE show_id = '550e8400-e29b-41d4-a716-446655440000';