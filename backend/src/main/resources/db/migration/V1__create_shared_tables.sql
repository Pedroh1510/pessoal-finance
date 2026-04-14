CREATE TABLE IF NOT EXISTS category (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    color      VARCHAR(7)   NOT NULL,
    is_system  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS recipient_category_rule (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_pattern VARCHAR(255) NOT NULL,
    category_id       UUID        NOT NULL REFERENCES category(id) ON DELETE CASCADE,
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS internal_account_rule (
    id         UUID       PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(255) NOT NULL,
    type       VARCHAR(20)  NOT NULL CHECK (type IN ('NAME', 'CPF', 'CNPJ')),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Categorias padrão do sistema
INSERT INTO category (name, color, is_system) VALUES
    ('Alimentação',   '#FF6B6B', TRUE),
    ('Transporte',    '#4ECDC4', TRUE),
    ('Saúde',         '#45B7D1', TRUE),
    ('Educação',      '#96CEB4', TRUE),
    ('Lazer',         '#FFEAA7', TRUE),
    ('Moradia',       '#DDA0DD', TRUE),
    ('Investimentos', '#98FB98', TRUE),
    ('Salário',       '#87CEEB', TRUE),
    ('Transferência', '#D3D3D3', TRUE),
    ('Outros',        '#F0E68C', TRUE)
ON CONFLICT DO NOTHING;
