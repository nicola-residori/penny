CREATE TABLE penny_catalog_products (
 id BIGSERIAL PRIMARY KEY,
 code VARCHAR(80) NOT NULL CHECK (length(trim(code))>0),
 name VARCHAR(200) NOT NULL CHECK (length(trim(name))>0),
 category VARCHAR(12) NOT NULL CHECK (category IN ('HARDWARE','SOFTWARE','SERVICE')),
 origin VARCHAR(12) NOT NULL CHECK (origin IN ('INTERNAL','THIRD_PARTY')),
 manufacturer VARCHAR(120), model VARCHAR(120), description TEXT,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT penny_catalog_identity UNIQUE (id,origin,category)
);
CREATE UNIQUE INDEX penny_catalog_code_unique ON penny_catalog_products(lower(code));
CREATE INDEX penny_catalog_origin_active_idx ON penny_catalog_products(origin,active,id);
-- No legacy origin can be inferred reliably. Preserve every field and classify as external.
ALTER TABLE penny_installed_products ADD COLUMN origin VARCHAR(12) NOT NULL DEFAULT 'THIRD_PARTY';
ALTER TABLE penny_installed_products ADD COLUMN catalog_product_id BIGINT;
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_installed_origin CHECK (origin IN ('INTERNAL','THIRD_PARTY'));
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_internal_catalog_required CHECK (origin <> 'INTERNAL' OR catalog_product_id IS NOT NULL);
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_installed_catalog_fk
 FOREIGN KEY (catalog_product_id,origin,category) REFERENCES penny_catalog_products(id,origin,category) ON DELETE RESTRICT ON UPDATE RESTRICT;
CREATE INDEX penny_installed_catalog_idx ON penny_installed_products(catalog_product_id);
INSERT INTO penny_permissions(code,description) VALUES ('CATALOG_READ','Visualizzare catalogo prodotti'),('CATALOG_WRITE','Gestire catalogo prodotti');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
WHERE (r.code IN ('ADMIN','SALES') AND p.code IN ('CATALOG_READ','CATALOG_WRITE')) OR (r.code='TECH' AND p.code='CATALOG_READ');
