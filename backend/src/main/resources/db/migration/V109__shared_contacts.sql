-- Existing identities, sites, soft deletion timestamps and trash entries are retained.
ALTER TABLE penny_customers ADD COLUMN lifecycle_status VARCHAR(12) NOT NULL DEFAULT 'CUSTOMER'
 CHECK (lifecycle_status IN ('PROSPECT','CUSTOMER'));
-- Reconstruct the newly introduced state from registered sales, including sales
-- later cancelled (cancellation does not erase the first-sale relationship).
-- Only the new column changes; trashed historical rows remain otherwise frozen.
ALTER TABLE penny_customers DISABLE TRIGGER penny_customers_trash_write_guard;
UPDATE penny_customers c SET lifecycle_status='PROSPECT' WHERE NOT EXISTS(SELECT 1 FROM penny_sales s WHERE s.customer_id=c.id);
ALTER TABLE penny_customers ENABLE TRIGGER penny_customers_trash_write_guard;

CREATE TABLE penny_contact_customers (
 contact_id BIGINT NOT NULL REFERENCES penny_customer_contacts(id) ON DELETE CASCADE,
 customer_id BIGINT NOT NULL REFERENCES penny_customers(id) ON DELETE CASCADE,
 site_id BIGINT REFERENCES penny_customer_sites(id) ON DELETE RESTRICT,
 role VARCHAR(120), is_primary BOOLEAN NOT NULL DEFAULT false,
 PRIMARY KEY(contact_id,customer_id)
);
CREATE INDEX penny_contact_customers_customer_idx ON penny_contact_customers(customer_id,contact_id);
CREATE INDEX penny_contact_customers_site_idx ON penny_contact_customers(site_id);
INSERT INTO penny_contact_customers(contact_id,customer_id,role,is_primary)
 SELECT id,customer_id,role,is_primary FROM penny_customer_contacts;

-- Legacy insert clients remain supported; the relation table is canonical thereafter.
CREATE FUNCTION penny_contact_legacy_link() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO penny_contact_customers(contact_id,customer_id,role,is_primary)
 VALUES (NEW.id,NEW.customer_id,NEW.role,NEW.is_primary);
 RETURN NEW;
END $$;
CREATE TRIGGER penny_contact_legacy_insert AFTER INSERT ON penny_customer_contacts
 FOR EACH ROW EXECUTE FUNCTION penny_contact_legacy_link();

CREATE FUNCTION penny_contact_link_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE contact_deleted timestamptz;
BEGIN
 SELECT deleted_at INTO contact_deleted FROM penny_customer_contacts WHERE id=NEW.contact_id FOR UPDATE;
 IF NOT FOUND OR contact_deleted IS NOT NULL THEN RAISE EXCEPTION 'Contact must be live' USING ERRCODE='23514'; END IF;
 PERFORM 1 FROM penny_customers WHERE id=NEW.customer_id AND deleted_at IS NULL FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Customer must be live' USING ERRCODE='23514'; END IF;
 IF NEW.site_id IS NOT NULL THEN
  PERFORM 1 FROM penny_customer_sites WHERE id=NEW.site_id AND customer_id=NEW.customer_id FOR SHARE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Site must belong to the same customer' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
-- Backfilled historical trashed rows above deliberately bypass this new-write guard.
CREATE TRIGGER penny_contact_link_write BEFORE INSERT OR UPDATE ON penny_contact_customers
 FOR EACH ROW EXECUTE FUNCTION penny_contact_link_guard();

-- Serialize removals on the identity row so concurrent last-unlinks cannot both commit.
CREATE FUNCTION penny_contact_link_delete_lock() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM 1 FROM penny_customer_contacts WHERE id=OLD.contact_id FOR UPDATE; RETURN OLD; END $$;
CREATE TRIGGER penny_contact_link_delete_lock BEFORE DELETE ON penny_contact_customers
 FOR EACH ROW EXECUTE FUNCTION penny_contact_link_delete_lock();
CREATE FUNCTION penny_contact_require_link() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE cid bigint;
BEGIN
 IF TG_TABLE_NAME='penny_customer_contacts' THEN cid:=NEW.id; ELSE cid:=OLD.contact_id; END IF;
 IF EXISTS(SELECT 1 FROM penny_customer_contacts WHERE id=cid)
 AND (NOT EXISTS(SELECT 1 FROM penny_contact_customers WHERE contact_id=cid)
 OR NOT EXISTS(SELECT 1 FROM penny_contact_customers l JOIN penny_customer_contacts c ON c.id=l.contact_id AND c.customer_id=l.customer_id WHERE c.id=cid)) THEN
  RAISE EXCEPTION 'Contact requires at least one customer or prospect' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER penny_contact_has_customer AFTER INSERT OR UPDATE ON penny_customer_contacts
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION penny_contact_require_link();
CREATE CONSTRAINT TRIGGER penny_contact_last_link AFTER DELETE OR UPDATE ON penny_contact_customers
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION penny_contact_require_link();

CREATE OR REPLACE FUNCTION penny_check_site_customer_move() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM penny_installed_products WHERE site_id=NEW.id AND customer_id<>NEW.customer_id)
 OR EXISTS(SELECT 1 FROM penny_workstations WHERE site_id=NEW.id AND customer_id<>NEW.customer_id)
 OR EXISTS(SELECT 1 FROM penny_contact_customers WHERE site_id=NEW.id AND customer_id<>NEW.customer_id) THEN
  RAISE EXCEPTION 'Linked site cannot change customer' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;

-- Never let the legacy customer_id cascade erase a shared identity. Restore the
-- customer and unlink its shared contacts before retrying permanent deletion.
CREATE FUNCTION penny_customer_shared_contact_purge_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM penny_customer_contacts c WHERE c.customer_id=OLD.id AND EXISTS(SELECT 1 FROM penny_contact_customers l WHERE l.contact_id=c.id AND l.customer_id<>OLD.id))
 OR EXISTS(SELECT 1 FROM penny_contact_customers l WHERE l.customer_id=OLD.id
  AND EXISTS(SELECT 1 FROM penny_contact_customers other WHERE other.contact_id=l.contact_id AND other.customer_id<>OLD.id)) THEN
  RAISE EXCEPTION 'Restore customer and remove shared contact associations before permanent deletion' USING ERRCODE='23514';
 END IF;
 RETURN OLD;
END $$;
CREATE TRIGGER penny_customer_shared_contact_purge BEFORE DELETE ON penny_customers
 FOR EACH ROW EXECUTE FUNCTION penny_customer_shared_contact_purge_guard();

-- The compatibility anchor may be in trash while another linked customer is live.
DROP TRIGGER penny_contacts_trash_write_guard ON penny_customer_contacts;
CREATE FUNCTION penny_contact_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='UPDATE' AND OLD.deleted_at IS NOT NULL AND NEW.deleted_at IS NOT NULL
 AND (to_jsonb(NEW)-'deleted_at') IS DISTINCT FROM (to_jsonb(OLD)-'deleted_at') THEN
  RAISE EXCEPTION 'Restore trashed contact before editing' USING ERRCODE='23514';
 END IF;
 IF TG_OP='INSERT' THEN PERFORM penny_assert_not_trashed('penny_customers',NEW.customer_id);
 ELSIF NEW.deleted_at IS NULL THEN
  PERFORM 1 FROM penny_customers cu JOIN penny_contact_customers l ON l.customer_id=cu.id
   WHERE l.contact_id=NEW.id AND cu.deleted_at IS NULL FOR SHARE OF cu;
  IF NOT FOUND THEN RAISE EXCEPTION 'Restore a linked customer first' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_contacts_trash_write_guard BEFORE INSERT OR UPDATE ON penny_customer_contacts
 FOR EACH ROW EXECUTE FUNCTION penny_contact_identity_guard();

-- Existing rows above retain CUSTOMER; future records start as prospects.
ALTER TABLE penny_customers ALTER COLUMN lifecycle_status SET DEFAULT 'PROSPECT';
CREATE FUNCTION penny_guard_customer_lifecycle() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.lifecycle_status IS DISTINCT FROM OLD.lifecycle_status THEN
  IF OLD.lifecycle_status='CUSTOMER' OR NOT EXISTS(SELECT 1 FROM penny_sales WHERE customer_id=NEW.id AND status='CONFIRMED') THEN
   RAISE EXCEPTION 'Commercial status changes automatically on the first sale' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_customer_lifecycle_guard BEFORE UPDATE OF lifecycle_status ON penny_customers FOR EACH ROW EXECUTE FUNCTION penny_guard_customer_lifecycle();
CREATE FUNCTION penny_lock_sale_customer_lifecycle() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM penny_customers WHERE id=NEW.customer_id FOR UPDATE;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_sales_lifecycle_lock BEFORE INSERT OR UPDATE OF customer_id,status ON penny_sales FOR EACH ROW EXECUTE FUNCTION penny_lock_sale_customer_lifecycle();
CREATE FUNCTION penny_promote_prospect_on_sale() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE promoted bigint;
BEGIN
 IF NEW.status='CONFIRMED' THEN
  UPDATE penny_customers SET lifecycle_status='CUSTOMER',updated_at=now() WHERE id=NEW.customer_id AND lifecycle_status='PROSPECT' RETURNING id INTO promoted;
  IF promoted IS NOT NULL THEN
   INSERT INTO penny_m2_audit(actor,action,entity_type,entity_id,metadata)
   VALUES (coalesce(nullif(current_setting('penny.actor',true),''),'system:first-sale'),'CUSTOMER_STATUS_CHANGED','CUSTOMER',promoted,jsonb_build_object('before','PROSPECT','after','CUSTOMER','saleId',NEW.id,'reason','FIRST_SALE'));
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_sales_promote_prospect AFTER INSERT OR UPDATE OF customer_id,status ON penny_sales FOR EACH ROW EXECUTE FUNCTION penny_promote_prospect_on_sale();
