ALTER TABLE penny_contract_renewals ADD COLUMN event_type varchar(20) NOT NULL DEFAULT 'RENEWAL' CHECK(event_type IN ('RENEWAL','CORRECTION'));
ALTER TABLE penny_contract_renewals ALTER COLUMN new_start DROP NOT NULL;
ALTER TABLE penny_contract_renewals ALTER COLUMN new_end DROP NOT NULL;
CREATE FUNCTION penny_intervention_link_guard() RETURNS trigger LANGUAGE plpgsql AS $$ DECLARE parent_customer bigint; child_customer bigint; parent_kind text; child_kind text; BEGIN
 SELECT customer_id,kind INTO parent_customer,parent_kind FROM penny_customer_records WHERE id=NEW.record_id FOR SHARE;
 SELECT customer_id,kind INTO child_customer,child_kind FROM penny_customer_records WHERE id=NEW.intervention_id FOR SHARE;
 IF parent_customer IS DISTINCT FROM child_customer OR parent_kind NOT IN ('TECHNICAL','DEVELOPMENT') OR child_kind IS DISTINCT FROM 'INTERVENTION' THEN RAISE EXCEPTION 'Incompatible intervention link' USING ERRCODE='23514'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER record_intervention_link_guard BEFORE INSERT OR UPDATE ON penny_record_interventions FOR EACH ROW EXECUTE FUNCTION penny_intervention_link_guard();
CREATE FUNCTION penny_linked_record_owner_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.customer_id IS DISTINCT FROM OLD.customer_id AND EXISTS(SELECT 1 FROM penny_record_interventions WHERE record_id=OLD.id OR intervention_id=OLD.id) THEN RAISE EXCEPTION 'Linked record customer cannot change' USING ERRCODE='23514'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER linked_record_owner_guard BEFORE UPDATE OF customer_id ON penny_customer_records FOR EACH ROW EXECUTE FUNCTION penny_linked_record_owner_guard();
