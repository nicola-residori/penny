ALTER TABLE penny_customers ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE penny_customer_contacts ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE penny_catalog_products ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE penny_installed_products ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE penny_workstations ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX penny_customers_deleted_idx ON penny_customers(deleted_at);
CREATE INDEX penny_contacts_deleted_idx ON penny_customer_contacts(deleted_at);
CREATE INDEX penny_catalog_deleted_idx ON penny_catalog_products(deleted_at);
CREATE INDEX penny_installed_deleted_idx ON penny_installed_products(deleted_at);
CREATE INDEX penny_workstations_deleted_idx ON penny_workstations(deleted_at);
CREATE TABLE penny_trash_entries (
 id BIGSERIAL PRIMARY KEY,
 entity_type VARCHAR(24) NOT NULL CHECK(entity_type IN ('CUSTOMER','CONTACT','CATALOG_PRODUCT','INSTALLED_PRODUCT','WORKSTATION')),
 entity_id BIGINT NOT NULL, label VARCHAR(200) NOT NULL, deleted_by VARCHAR(320) NOT NULL,
 deleted_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 blocked_reason VARCHAR(500), last_cleanup_at TIMESTAMPTZ,
 UNIQUE(entity_type,entity_id),
 CHECK(expires_at=deleted_at+interval '720 hours')
);
CREATE INDEX penny_trash_expiry_idx ON penny_trash_entries(expires_at,id);

CREATE FUNCTION penny_assert_not_trashed(parent_table text,parent_id bigint) RETURNS void LANGUAGE plpgsql AS $$
DECLARE deletion timestamptz;
BEGIN
 IF parent_id IS NULL THEN RETURN; END IF;
 EXECUTE format('SELECT deleted_at FROM %I WHERE id=$1 FOR SHARE',parent_table) INTO deletion USING parent_id;
 IF deletion IS NOT NULL THEN RAISE EXCEPTION 'Referenced record is in trash' USING ERRCODE='23514'; END IF;
END $$;
CREATE FUNCTION penny_guard_trash_reference() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND to_jsonb(OLD)->>'deleted_at' IS NOT NULL AND to_jsonb(NEW)->>'deleted_at' IS NOT NULL AND (to_jsonb(NEW)-'deleted_at') IS DISTINCT FROM (to_jsonb(OLD)-'deleted_at') THEN
  RAISE EXCEPTION 'Restore trashed record before editing it' USING ERRCODE='23514';
 END IF;
 IF to_jsonb(NEW)->>'deleted_at' IS NULL THEN
  IF TG_TABLE_NAME IN ('penny_customer_contacts','penny_installed_products','penny_workstations','penny_sales') THEN PERFORM penny_assert_not_trashed('penny_customers',NEW.customer_id); END IF;
  IF TG_TABLE_NAME IN ('penny_installed_products','penny_sales') THEN PERFORM penny_assert_not_trashed('penny_catalog_products',NEW.catalog_product_id); END IF;
  IF TG_TABLE_NAME='penny_installed_products' THEN PERFORM penny_assert_not_trashed('penny_workstations',NEW.workstation_id); END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_customers_trash_write_guard BEFORE UPDATE ON penny_customers FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_reference();
CREATE TRIGGER penny_contacts_trash_write_guard BEFORE INSERT OR UPDATE ON penny_customer_contacts FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_reference();
CREATE TRIGGER penny_catalog_trash_write_guard BEFORE UPDATE ON penny_catalog_products FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_reference();
CREATE TRIGGER penny_installed_trash_write_guard BEFORE INSERT OR UPDATE ON penny_installed_products FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_reference();
CREATE TRIGGER penny_workstations_trash_write_guard BEFORE INSERT OR UPDATE ON penny_workstations FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_reference();
CREATE TRIGGER penny_sales_trash_reference_guard BEFORE INSERT OR UPDATE ON penny_sales FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_reference();

CREATE FUNCTION penny_guard_trash_transition() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE linked boolean := false;
BEGIN
 IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL THEN
  CASE TG_TABLE_NAME
   WHEN 'penny_customers' THEN SELECT EXISTS(SELECT 1 FROM penny_sales WHERE customer_id=OLD.id) OR EXISTS(SELECT 1 FROM penny_installed_products WHERE customer_id=OLD.id) OR EXISTS(SELECT 1 FROM penny_workstations WHERE customer_id=OLD.id) INTO linked;
   WHEN 'penny_catalog_products' THEN SELECT EXISTS(SELECT 1 FROM penny_sales WHERE catalog_product_id=OLD.id) OR EXISTS(SELECT 1 FROM penny_installed_products WHERE catalog_product_id=OLD.id) INTO linked;
   WHEN 'penny_workstations' THEN SELECT EXISTS(SELECT 1 FROM penny_installed_products WHERE workstation_id=OLD.id) INTO linked;
   WHEN 'penny_installed_products' THEN SELECT EXISTS(SELECT 1 FROM penny_sale_allocations WHERE installed_product_id=OLD.id) INTO linked;
   ELSE linked:=false;
  END CASE;
  IF linked THEN RAISE EXCEPTION 'Referenced record cannot enter trash' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_customer_trash_transition BEFORE UPDATE OF deleted_at ON penny_customers FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_transition();
CREATE TRIGGER penny_catalog_trash_transition BEFORE UPDATE OF deleted_at ON penny_catalog_products FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_transition();
CREATE TRIGGER penny_station_trash_transition BEFORE UPDATE OF deleted_at ON penny_workstations FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_transition();
CREATE TRIGGER penny_install_trash_transition BEFORE UPDATE OF deleted_at ON penny_installed_products FOR EACH ROW EXECUTE FUNCTION penny_guard_trash_transition();
INSERT INTO penny_permissions(code,description) VALUES ('TRASH_READ','Visualizzare cestino'),('TRASH_DELETE','Spostare elementi nel cestino'),('TRASH_RESTORE','Ripristinare elementi dal cestino');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p WHERE r.code IN ('ADMIN','SALES','TECH') AND p.code IN ('TRASH_READ','TRASH_DELETE','TRASH_RESTORE');
