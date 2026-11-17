CREATE TABLE member_points (
    member_id  VARCHAR(64) PRIMARY KEY,
    balance    BIGINT      NOT NULL CHECK (balance >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The primary key on event_id is what makes a repeated event impossible to credit twice.
CREATE TABLE processed_event (
    event_id     VARCHAR(64) PRIMARY KEY,
    member_id    VARCHAR(64) NOT NULL,
    points       BIGINT      NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
