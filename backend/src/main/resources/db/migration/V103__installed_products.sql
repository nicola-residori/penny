CREATE TABLE penny_installed_products (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES penny_customers(id) ON DELETE RESTRICT,
 category VARCHAR(12) NOT NULL CHECK (category IN ('HARDWARE','SOFTWARE','SERVICE')),
 name VARCHAR(200) NOT NULL CHECK (length(trim(name))>0),
 manufacturer VARCHAR(120), model VARCHAR(120), serial_number VARCHAR(160), license_key VARCHAR(240),
 installed_on DATE, warranty_until DATE, license_expires_on DATE,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','MAINTENANCE','RETIRED')),
 notes TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT penny_product_warranty_dates CHECK (warranty_until IS NULL OR installed_on IS NULL OR warranty_until>=installed_on)
);
CREATE INDEX penny_installed_products_customer_idx ON penny_installed_products(customer_id,id);
CREATE INDEX penny_installed_products_status_idx ON penny_installed_products(status);
CREATE UNIQUE INDEX penny_installed_products_serial_unique ON penny_installed_products(lower(serial_number)) WHERE serial_number IS NOT NULL AND serial_number<>'';
INSERT INTO penny_permissions(code,description) VALUES ('PRODUCTS_READ','Visualizzare prodotti installati'),('PRODUCTS_WRITE','Creare e modificare prodotti installati');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
WHERE r.code='ADMIN' AND p.code IN ('PRODUCTS_READ','PRODUCTS_WRITE');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
WHERE r.code='SALES' AND p.code IN ('PRODUCTS_READ','PRODUCTS_WRITE');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
WHERE r.code='TECH' AND p.code IN ('PRODUCTS_READ','PRODUCTS_WRITE');
