CREATE TABLE IF NOT EXISTS bank_account (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_name          VARCHAR(20)  NOT NULL,
    account_identifier VARCHAR(255) NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transaction (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  UUID          NOT NULL REFERENCES bank_account(id),
    date        TIMESTAMP     NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    recipient   VARCHAR(255),
    description VARCHAR(500),
    category_id UUID          REFERENCES category(id),
    type        VARCHAR(20)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'INTERNAL_TRANSFER')),
    raw_text    TEXT,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS internal_transfer (
    id                     UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    from_transaction_id    UUID    NOT NULL UNIQUE REFERENCES transaction(id),
    to_transaction_id      UUID    NOT NULL UNIQUE REFERENCES transaction(id),
    detected_automatically BOOLEAN NOT NULL DEFAULT TRUE
);
