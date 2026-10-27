CREATE TABLE penny_customers (
 id BIGSERIAL PRIMARY KEY,
 kind VARCHAR(12) NOT NULL CHECK (kind IN ('COMPANY','PERSON')),
 name VARCHAR(200) NOT NULL,
 vat_number VARCHAR(32),
 tax_code VARCHAR(32),
 email VARCHAR(254),
 phone VARCHAR(40),
 street VARCHAR(240),
 postal_code VARCHAR(20),
 city VARCHAR(120),
 province VARCHAR(100),
 country VARCHAR(2) NOT NULL DEFAULT 'IT',
 notes TEXT,
 archived BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT penny_customers_name_nonempty CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX penny_customers_vat_unique ON penny_customers (upper(vat_number)) WHERE vat_number IS NOT NULL AND vat_number <> '';
CREATE INDEX penny_customers_search_idx ON penny_customers (lower(name));
CREATE INDEX penny_customers_archived_idx ON penny_customers (archived,id);
CREATE TABLE penny_customer_contacts (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES penny_customers(id) ON DELETE CASCADE,
 full_name VARCHAR(200) NOT NULL,
 role VARCHAR(120),
 email VARCHAR(254),
 phone VARCHAR(40),
 is_primary BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT penny_contacts_name_nonempty CHECK (length(trim(full_name)) > 0)
);
CREATE INDEX penny_customer_contacts_customer_idx ON penny_customer_contacts(customer_id);
INSERT INTO penny_permissions(code,description) VALUES ('CUSTOMERS_READ','Visualizzare clienti'),('CUSTOMERS_WRITE','Creare e modificare clienti');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
WHERE r.code='ADMIN' AND p.code IN ('CUSTOMERS_READ','CUSTOMERS_WRITE');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
WHERE r.code='SALES' AND p.code IN ('CUSTOMERS_READ','CUSTOMERS_WRITE');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
WHERE r.code='TECH' AND p.code='CUSTOMERS_READ';
