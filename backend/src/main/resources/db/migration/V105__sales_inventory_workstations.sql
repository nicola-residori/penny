CREATE TABLE penny_workstations (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES penny_customers(id) ON DELETE RESTRICT,
 name VARCHAR(160) NOT NULL CHECK (length(trim(name))>0),
 location VARCHAR(240), notes TEXT, active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(id,customer_id)
);
CREATE UNIQUE INDEX penny_workstations_name_unique ON penny_workstations(customer_id,lower(name));
ALTER TABLE penny_installed_products ADD COLUMN workstation_id BIGINT;
ALTER TABLE penny_installed_products ADD COLUMN allocation_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_installed_workstation_fk FOREIGN KEY(workstation_id,customer_id) REFERENCES penny_workstations(id,customer_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_installed_sale_identity UNIQUE(id,customer_id,catalog_product_id);
CREATE INDEX penny_installed_workstation_idx ON penny_installed_products(workstation_id);

CREATE TABLE penny_sales (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES penny_customers(id) ON DELETE RESTRICT,
 catalog_product_id BIGINT NOT NULL,
 product_code VARCHAR(80) NOT NULL, product_name VARCHAR(200) NOT NULL,
 origin VARCHAR(12) NOT NULL, category VARCHAR(12) NOT NULL,
 sold_on DATE NOT NULL,
 quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 1000000),
 allocation_version BIGINT NOT NULL DEFAULT 0,
 unit_price NUMERIC(11,2) NOT NULL DEFAULT 0 CHECK(unit_price>=0),
 reference VARCHAR(200), notes TEXT,
 status VARCHAR(12) NOT NULL DEFAULT 'CONFIRMED' CHECK(status IN ('CONFIRMED','CANCELLED')),
 cancellation_reason VARCHAR(1000), cancelled_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT penny_sales_cancel_reason CHECK(status<>'CANCELLED' OR length(trim(cancellation_reason))>0 AND cancellation_reason IS NOT NULL),
 CONSTRAINT penny_sales_catalog_fk FOREIGN KEY(catalog_product_id,origin,category) REFERENCES penny_catalog_products(id,origin,category) ON UPDATE RESTRICT ON DELETE RESTRICT,
 UNIQUE(id,customer_id,catalog_product_id)
);
CREATE INDEX penny_sales_customer_date_idx ON penny_sales(customer_id,sold_on,id);
CREATE INDEX penny_sales_product_idx ON penny_sales(catalog_product_id,status);
CREATE TABLE penny_sale_allocations (
 id BIGSERIAL PRIMARY KEY,
 sale_id BIGINT NOT NULL, installed_product_id BIGINT NOT NULL,
 customer_id BIGINT NOT NULL, catalog_product_id BIGINT NOT NULL,
 quantity INTEGER NOT NULL CHECK(quantity BETWEEN 1 AND 1000000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT penny_alloc_sale_fk FOREIGN KEY(sale_id,customer_id,catalog_product_id) REFERENCES penny_sales(id,customer_id,catalog_product_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 CONSTRAINT penny_alloc_installed_fk FOREIGN KEY(installed_product_id,customer_id,catalog_product_id) REFERENCES penny_installed_products(id,customer_id,catalog_product_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
 UNIQUE(sale_id,installed_product_id)
);
CREATE INDEX penny_alloc_installed_idx ON penny_sale_allocations(installed_product_id);

-- Serialize every allocation against its sale row; enforce limits even outside the API.
CREATE FUNCTION penny_check_allocation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE sale_status VARCHAR(12); sale_quantity INTEGER; allocated BIGINT; installed_status VARCHAR(16); catalog_active BOOLEAN; catalog_id BIGINT;
BEGIN
 IF TG_OP='DELETE' THEN
  UPDATE penny_sales SET allocation_version=allocation_version+1 WHERE id=OLD.sale_id;
  UPDATE penny_installed_products SET allocation_version=allocation_version+1 WHERE id=OLD.installed_product_id;
  RETURN OLD;
 END IF;
 IF TG_OP='UPDATE' AND (NEW.sale_id<>OLD.sale_id OR NEW.installed_product_id<>OLD.installed_product_id OR NEW.customer_id<>OLD.customer_id OR NEW.catalog_product_id<>OLD.catalog_product_id) THEN
  RAISE EXCEPTION 'Allocation identity is immutable' USING ERRCODE='23514';
 END IF;
 SELECT catalog_product_id INTO catalog_id FROM penny_sales WHERE id=NEW.sale_id;
 -- Consistent catalog -> sale -> installation ordering. A changed guard also invalidates stale RR snapshots.
 SELECT active INTO catalog_active FROM penny_catalog_products WHERE id=catalog_id FOR SHARE;
 IF NOT coalesce(catalog_active,false) AND (TG_OP='INSERT' OR NEW.quantity>OLD.quantity) THEN RAISE EXCEPTION 'Catalog product is archived' USING ERRCODE='23514'; END IF;
 UPDATE penny_sales SET allocation_version=allocation_version+1 WHERE id=NEW.sale_id RETURNING status,quantity INTO sale_status,sale_quantity;
 IF NOT FOUND THEN RAISE EXCEPTION 'Sale does not exist' USING ERRCODE='23503'; END IF;
 IF sale_status<>'CONFIRMED' THEN RAISE EXCEPTION 'Sale is cancelled' USING ERRCODE='23514'; END IF;
 UPDATE penny_installed_products SET allocation_version=allocation_version+1 WHERE id=NEW.installed_product_id RETURNING status INTO installed_status;
 IF installed_status='RETIRED' THEN RAISE EXCEPTION 'Installation is retired' USING ERRCODE='23514'; END IF;
 SELECT coalesce(sum(quantity),0) INTO allocated FROM penny_sale_allocations WHERE sale_id=NEW.sale_id AND id<>NEW.id;
 IF allocated+NEW.quantity>sale_quantity THEN RAISE EXCEPTION 'Insufficient unallocated quantity' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_allocation_guard BEFORE INSERT OR UPDATE OR DELETE ON penny_sale_allocations FOR EACH ROW EXECUTE FUNCTION penny_check_allocation();
CREATE FUNCTION penny_check_sale_changes() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE allocated BIGINT;
BEGIN
 SELECT coalesce(sum(quantity),0) INTO allocated FROM penny_sale_allocations WHERE sale_id=OLD.id;
 IF NEW.quantity<allocated OR (NEW.status='CANCELLED' AND allocated>0) THEN RAISE EXCEPTION 'Release allocations first' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_sale_guard BEFORE UPDATE ON penny_sales FOR EACH ROW EXECUTE FUNCTION penny_check_sale_changes();
CREATE FUNCTION penny_check_retired_installation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status='RETIRED' AND EXISTS(SELECT 1 FROM penny_sale_allocations WHERE installed_product_id=OLD.id) THEN RAISE EXCEPTION 'Release allocations before retiring' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_installed_retired_guard BEFORE UPDATE OF status ON penny_installed_products FOR EACH ROW EXECUTE FUNCTION penny_check_retired_installation();

CREATE TABLE penny_m2_audit (
 id BIGSERIAL PRIMARY KEY, actor VARCHAR(320) NOT NULL, action VARCHAR(80) NOT NULL,
 entity_type VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL,
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX penny_m2_audit_entity_idx ON penny_m2_audit(entity_type,entity_id,id);
INSERT INTO penny_permissions(code,description) VALUES
 ('SALES_READ','Visualizzare vendite e importi'),('SALES_WRITE','Registrare e annullare vendite'),
 ('INVENTORY_READ','Visualizzare quantità vendute e disponibili'),
 ('ALLOCATIONS_READ','Visualizzare allocazioni'),('ALLOCATIONS_WRITE','Assegnare e liberare quantità'),
 ('WORKSTATIONS_READ','Visualizzare postazioni'),('WORKSTATIONS_WRITE','Gestire postazioni');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
WHERE (r.code IN ('ADMIN','SALES') AND p.code IN ('SALES_READ','SALES_WRITE','INVENTORY_READ','ALLOCATIONS_READ','ALLOCATIONS_WRITE','WORKSTATIONS_READ','WORKSTATIONS_WRITE'))
 OR (r.code='TECH' AND p.code IN ('INVENTORY_READ','ALLOCATIONS_READ','ALLOCATIONS_WRITE','WORKSTATIONS_READ','WORKSTATIONS_WRITE'));
