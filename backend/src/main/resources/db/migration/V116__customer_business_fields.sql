ALTER TABLE penny_customers
 ADD COLUMN sector varchar(200), ADD COLUMN website varchar(500), ADD COLUMN pec varchar(254),
 ADD COLUMN sdi_code varchar(40), ADD COLUMN default_payment varchar(500),
 ADD COLUMN bank_details1 text, ADD COLUMN bank_details2 text, ADD COLUMN administrative_notes text,
 ADD COLUMN commercial_contact_id bigint, ADD COLUMN administrative_contact_id bigint,
 ADD COLUMN is_insolvent boolean NOT NULL DEFAULT false, ADD COLUMN is_collaborator boolean NOT NULL DEFAULT false,
 ADD COLUMN company_status varchar(20) NOT NULL DEFAULT 'ACTIVE',
 ADD COLUMN replacement_customer_id bigint REFERENCES penny_customers(id), ADD COLUMN previous_customer_id bigint REFERENCES penny_customers(id),
 ADD CONSTRAINT customer_company_status CHECK(company_status IN ('ACTIVE','OBSOLETE','REPLACED','CEASED')),
 ADD CONSTRAINT customer_replacement_required CHECK(company_status <> 'REPLACED' OR replacement_customer_id IS NOT NULL),
 ADD CONSTRAINT customer_commercial_contact FOREIGN KEY(commercial_contact_id,id) REFERENCES penny_contact_customers(contact_id,customer_id) DEFERRABLE INITIALLY DEFERRED,
 ADD CONSTRAINT customer_administrative_contact FOREIGN KEY(administrative_contact_id,id) REFERENCES penny_contact_customers(contact_id,customer_id) DEFERRABLE INITIALLY DEFERRED;

CREATE FUNCTION penny_validate_customer_business() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE business_reference_id bigint; reference_index integer; new_references bigint[]; old_references bigint[];
BEGIN
 -- Serialize graph edits to avoid concurrent cycles.
 PERFORM pg_advisory_xact_lock(116116);
 new_references := ARRAY[NEW.replacement_customer_id,NEW.previous_customer_id];
 IF TG_OP='UPDATE' THEN old_references := ARRAY[OLD.replacement_customer_id,OLD.previous_customer_id]; ELSE old_references := ARRAY[NULL::bigint,NULL::bigint]; END IF;
 FOR reference_index IN 1..2 LOOP
  business_reference_id := new_references[reference_index];
  IF business_reference_id IS DISTINCT FROM old_references[reference_index] AND business_reference_id IS NOT NULL AND (business_reference_id=NEW.id OR NOT EXISTS(SELECT 1 FROM penny_customers WHERE id=business_reference_id AND deleted_at IS NULL)) THEN
   RAISE EXCEPTION 'Il riferimento aziendale deve essere un altro cliente disponibile' USING ERRCODE='23514';
  END IF;
 END LOOP;
 IF new_references IS DISTINCT FROM old_references AND EXISTS (
  WITH RECURSIVE edges(source,target) AS (
   SELECT id,replacement_customer_id FROM penny_customers WHERE id<>NEW.id AND replacement_customer_id IS NOT NULL
   UNION SELECT previous_customer_id,id FROM penny_customers WHERE id<>NEW.id AND previous_customer_id IS NOT NULL
   UNION SELECT NEW.id,NEW.replacement_customer_id WHERE NEW.replacement_customer_id IS NOT NULL
   UNION SELECT NEW.previous_customer_id,NEW.id WHERE NEW.previous_customer_id IS NOT NULL
  ), reachable(id) AS (SELECT edge.target FROM edges edge WHERE edge.source=NEW.id UNION SELECT e.target FROM edges e JOIN reachable r ON e.source=r.id)
  SELECT 1 FROM reachable WHERE id=NEW.id) THEN RAISE EXCEPTION 'Ciclo tra aziende precedenti e sostitutive' USING ERRCODE='23514'; END IF;
 new_references := ARRAY[NEW.commercial_contact_id,NEW.administrative_contact_id];
 IF TG_OP='UPDATE' THEN old_references := ARRAY[OLD.commercial_contact_id,OLD.administrative_contact_id]; ELSE old_references := ARRAY[NULL::bigint,NULL::bigint]; END IF;
 FOR reference_index IN 1..2 LOOP
  business_reference_id := new_references[reference_index];
  IF business_reference_id IS DISTINCT FROM old_references[reference_index] AND business_reference_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM penny_customer_contacts c JOIN penny_contact_customers l ON l.contact_id=c.id WHERE c.id=business_reference_id AND c.deleted_at IS NULL AND l.customer_id=NEW.id) THEN
   RAISE EXCEPTION 'Il referente deve essere un contatto disponibile associato a questo cliente' USING ERRCODE='23514'; END IF;
 END LOOP;
 RETURN NEW;
END $$;
CREATE TRIGGER customer_business_validation BEFORE INSERT OR UPDATE ON penny_customers FOR EACH ROW EXECUTE FUNCTION penny_validate_customer_business();
CREATE FUNCTION penny_protect_designated_contact() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL AND EXISTS(SELECT 1 FROM penny_customers WHERE commercial_contact_id=NEW.id OR administrative_contact_id=NEW.id) THEN
 RAISE EXCEPTION 'Rimuovere prima il contatto dai referenti commerciali o amministrativi' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER designated_contact_protection BEFORE UPDATE OF deleted_at ON penny_customer_contacts FOR EACH ROW EXECUTE FUNCTION penny_protect_designated_contact();

-- Keep incoming business references live; callers may clear them explicitly before trashing.
CREATE FUNCTION penny_protect_referenced_customer() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.deleted_at IS NOT NULL AND OLD.deleted_at IS NULL THEN
  PERFORM pg_advisory_xact_lock(116116);
  IF EXISTS(SELECT 1 FROM penny_customers c WHERE c.id<>OLD.id AND c.deleted_at IS NULL
    AND (c.previous_customer_id=OLD.id OR c.replacement_customer_id=OLD.id)) THEN
   RAISE EXCEPTION 'Rimuovere prima i riferimenti ad azienda precedente o sostitutiva dalle altre schede cliente' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER referenced_customer_protection BEFORE UPDATE OF deleted_at ON penny_customers
 FOR EACH ROW EXECUTE FUNCTION penny_protect_referenced_customer();
