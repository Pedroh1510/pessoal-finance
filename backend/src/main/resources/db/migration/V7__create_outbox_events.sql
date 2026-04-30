CREATE TABLE outbox_events (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_name VARCHAR(100) NOT NULL,
    payload    JSONB        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published  BOOLEAN      NOT NULL DEFAULT false
);

CREATE INDEX idx_outbox_events_published ON outbox_events(published) WHERE published = false;
