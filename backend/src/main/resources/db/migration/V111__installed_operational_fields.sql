-- Unknown historical counts and suppliers stay NULL; no sales are inferred.
ALTER TABLE penny_installed_products ADD COLUMN supplier VARCHAR(200);
ALTER TABLE penny_installed_products ADD COLUMN quantity INTEGER CHECK (quantity IS NULL OR quantity BETWEEN 1 AND 1000000);
ALTER TABLE penny_installed_products ADD COLUMN activated_users INTEGER CHECK (activated_users IS NULL OR activated_users BETWEEN 0 AND 1000000);
