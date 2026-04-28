ALTER TABLE transaction            ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE transaction            ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE bank_account           ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE bank_account           ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE internal_transfer      ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE internal_transfer      ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE market_purchase        ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE market_purchase        ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE market_item            ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE market_item            ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE category               ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE category               ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE recipient_category_rule ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE recipient_category_rule ADD COLUMN updated_by UUID REFERENCES users(id);

ALTER TABLE internal_account_rule  ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE internal_account_rule  ADD COLUMN updated_by UUID REFERENCES users(id);
