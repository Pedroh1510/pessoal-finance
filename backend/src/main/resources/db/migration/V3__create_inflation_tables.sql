CREATE TABLE IF NOT EXISTS market_purchase (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    chave      VARCHAR(44)   NOT NULL UNIQUE,
    date       DATE          NOT NULL,
    emitente   VARCHAR(500)  NOT NULL,
    file_name  VARCHAR(255)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS market_item (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id   UUID           NOT NULL REFERENCES market_purchase(id) ON DELETE CASCADE,
    product_code  VARCHAR(50),
    ncm           VARCHAR(20)    NOT NULL,
    description   VARCHAR(500)   NOT NULL,
    quantity      NUMERIC(12,4)  NOT NULL,
    unit_price    NUMERIC(12,2)  NOT NULL,
    total_price   NUMERIC(12,2)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_market_item_ncm         ON market_item(ncm);
CREATE INDEX IF NOT EXISTS idx_market_item_purchase_id ON market_item(purchase_id);
CREATE INDEX IF NOT EXISTS idx_market_purchase_date    ON market_purchase(date);
CREATE INDEX IF NOT EXISTS idx_market_purchase_chave   ON market_purchase(chave);
