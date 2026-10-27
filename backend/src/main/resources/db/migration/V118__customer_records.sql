CREATE TABLE penny_customer_records (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES penny_customers(id) ON DELETE RESTRICT,
 site_id BIGINT REFERENCES penny_customer_sites(id) ON DELETE RESTRICT,
 kind VARCHAR(20) NOT NULL CHECK(kind IN ('TRAINING','CONTRACT','DEVELOPMENT','TECHNICAL','COMMERCIAL')),
 name VARCHAR(200) NOT NULL CHECK(length(trim(name))>0),
 product_type_id BIGINT REFERENCES penny_product_types(id) ON DELETE RESTRICT,
 sale_id BIGINT REFERENCES penny_sales(id) ON DELETE RESTRICT,
 starts_on DATE, expires_on DATE, status VARCHAR(24) NOT NULL,
 notes TEXT, details JSONB NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(details)='object'),
 deleted_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(expires_on IS NULL OR starts_on IS NULL OR expires_on>=starts_on),
 CHECK((kind='CONTRACT' AND status IN ('ACTIVE','EXPIRING','SUSPENDED','INSOLVENCY','EXPIRED','CANCELLED')) OR (kind<>'CONTRACT' AND status IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED')))
);
CREATE TABLE penny_record_participants (
 id BIGSERIAL PRIMARY KEY, record_id BIGINT NOT NULL REFERENCES penny_customer_records(id) ON DELETE CASCADE,
 contact_id BIGINT REFERENCES penny_customer_contacts(id) ON DELETE SET NULL,
 name VARCHAR(200) NOT NULL CHECK(length(trim(name))>0)
);
CREATE INDEX penny_records_customer_idx ON penny_customer_records(customer_id,kind);
CREATE INDEX penny_records_deadline_idx ON penny_customer_records(expires_on) WHERE deleted_at IS NULL;
ALTER TABLE penny_trash_entries DROP CONSTRAINT penny_trash_entries_entity_type_check;
ALTER TABLE penny_trash_entries ADD CONSTRAINT penny_trash_entries_entity_type_check CHECK(entity_type IN ('CUSTOMER','CONTACT','CATALOG_PRODUCT','INSTALLED_PRODUCT','WORKSTATION','CUSTOMER_RECORD'));
INSERT INTO penny_permissions(code,description) VALUES ('TRAINING_READ','Visualizzare formazione'),('TRAINING_WRITE','Gestire formazione'),('CONTRACTS_READ','Visualizzare contratti'),('CONTRACTS_WRITE','Gestire contratti'),('ACTIVITIES_READ','Visualizzare sviluppi e attività'),('ACTIVITIES_WRITE','Gestire sviluppi e attività');
INSERT INTO penny_role_permissions(role_id,permission_code,effect) SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p WHERE r.code='ADMIN' AND p.code IN ('TRAINING_READ','TRAINING_WRITE','CONTRACTS_READ','CONTRACTS_WRITE','ACTIVITIES_READ','ACTIVITIES_WRITE');
CREATE FUNCTION penny_guard_customer_record() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE op text; blocked boolean;
BEGIN
 IF TG_TABLE_NAME='penny_customers' THEN
  IF OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL AND EXISTS(SELECT 1 FROM penny_customer_records WHERE customer_id=OLD.id) THEN RAISE EXCEPTION 'Customer has records' USING ERRCODE='23514'; END IF;
  RETURN NEW;
 END IF;
 IF TG_OP='UPDATE' AND OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NOT NULL AND (to_jsonb(NEW)-'deleted_at') IS DISTINCT FROM (to_jsonb(OLD)-'deleted_at') THEN RAISE EXCEPTION 'Restore record before editing' USING ERRCODE='23514'; END IF;
 IF NEW.deleted_at IS NULL THEN
  PERFORM penny_assert_not_trashed('penny_customers',NEW.customer_id);
  IF NEW.site_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM penny_customer_sites WHERE id=NEW.site_id AND customer_id=NEW.customer_id) THEN RAISE EXCEPTION 'Site belongs to another customer' USING ERRCODE='23514'; END IF;
  IF NEW.sale_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM penny_sales WHERE id=NEW.sale_id AND customer_id=NEW.customer_id) THEN RAISE EXCEPTION 'Sale belongs to another customer' USING ERRCODE='23514'; END IF;
  IF NEW.product_type_id IS NOT NULL THEN
   SELECT c.operational_kind,t.obsolete OR NOT c.active INTO op,blocked FROM penny_product_types t JOIN penny_product_categories c ON c.id=t.category_id WHERE t.id=NEW.product_type_id FOR SHARE OF t,c;
   IF op IS DISTINCT FROM NEW.kind THEN RAISE EXCEPTION 'Incompatible product type' USING ERRCODE='23514'; END IF;
   IF blocked AND (TG_OP='INSERT' OR NEW.product_type_id IS DISTINCT FROM OLD.product_type_id) THEN RAISE EXCEPTION 'Obsolete product type' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_records_guard BEFORE INSERT OR UPDATE ON penny_customer_records FOR EACH ROW EXECUTE FUNCTION penny_guard_customer_record();
CREATE TRIGGER penny_customer_records_trash_guard BEFORE UPDATE OF deleted_at ON penny_customers FOR EACH ROW EXECUTE FUNCTION penny_guard_customer_record();
CREATE FUNCTION penny_guard_record_participant() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner bigint; record_kind text;
BEGIN
 SELECT customer_id,kind INTO owner,record_kind FROM penny_customer_records WHERE id=NEW.record_id FOR SHARE;
 IF record_kind IS DISTINCT FROM 'TRAINING' THEN RAISE EXCEPTION 'Participants require training' USING ERRCODE='23514'; END IF;
 IF NEW.contact_id IS NOT NULL THEN
  PERFORM 1 FROM penny_customer_contacts c JOIN penny_contact_customers l ON l.contact_id=c.id WHERE c.id=NEW.contact_id AND l.customer_id=owner AND c.deleted_at IS NULL FOR SHARE OF c,l;
  IF NOT FOUND THEN RAISE EXCEPTION 'Participant not associated with customer' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_record_participant_guard BEFORE INSERT OR UPDATE OF contact_id,record_id ON penny_record_participants FOR EACH ROW EXECUTE FUNCTION penny_guard_record_participant();
