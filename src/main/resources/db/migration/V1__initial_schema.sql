CREATE TABLE shows (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price_paise BIGINT NOT NULL CHECK (price_paise >= 0),
    per_user_limit INTEGER NOT NULL DEFAULT 4 CHECK (per_user_limit > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE seats (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    seat_number VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_seat_show_number UNIQUE (show_id, seat_number),
    CONSTRAINT ck_seat_status CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED'))
);

CREATE INDEX idx_seats_show_status ON seats(show_id, status);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(200) NOT NULL,
    amount_paise BIGINT NOT NULL CHECK (amount_paise >= 0),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT ck_reservation_status CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

CREATE INDEX idx_reservations_show_user ON reservations(show_id, user_id);

CREATE TABLE reservation_seats (
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    seat_id UUID NOT NULL REFERENCES seats(id),
    PRIMARY KEY (reservation_id, seat_id),
    CONSTRAINT uq_reservation_seat UNIQUE (seat_id, reservation_id)
);

CREATE TABLE user_show (
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(200) NOT NULL,
    seat_count INTEGER NOT NULL DEFAULT 0 CHECK (seat_count >= 0),
    PRIMARY KEY (show_id, user_id)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    show_id UUID NOT NULL REFERENCES shows(id),
    user_id VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    response_status INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency UNIQUE (show_id, user_id, idempotency_key)
);

CREATE INDEX idx_idempotency_reservation ON idempotency_keys(reservation_id);
