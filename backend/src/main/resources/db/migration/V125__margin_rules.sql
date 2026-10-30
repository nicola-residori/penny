CREATE TABLE penny_margin_profiles(id bigserial PRIMARY KEY,name varchar(200) NOT NULL,components jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE penny_catalog_products ADD CONSTRAINT catalog_margin_profile_fk FOREIGN KEY(margin_profile_id) REFERENCES penny_margin_profiles(id);
CREATE TABLE penny_margin_rules(id bigserial PRIMARY KEY,profile_id bigint NOT NULL REFERENCES penny_margin_profiles(id),previous_id bigint REFERENCES penny_margin_rules(id),name varchar(200) NOT NULL,component varchar(100) NOT NULL,calculation varchar(10) NOT NULL CHECK(calculation IN ('PERCENT','FIXED')),value numeric(20,6) NOT NULL CHECK(value>=0),sign int NOT NULL CHECK(sign IN (-1,1)),conditions jsonb NOT NULL DEFAULT '{}',valid_from date NOT NULL,valid_to date,priority int NOT NULL DEFAULT 0,status varchar(15) NOT NULL CHECK(status IN ('DRAFT','ACTIVE','EXPIRED','CANCELLED')),reference text,created_by varchar(254) NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),CHECK(valid_to IS NULL OR valid_to>=valid_from));
CREATE TABLE penny_margin_snapshots(id bigserial PRIMARY KEY,sale_id bigint NOT NULL REFERENCES penny_sales(id),revision int NOT NULL,component varchar(100) NOT NULL,rule_id bigint REFERENCES penny_margin_rules(id),rule_snapshot jsonb NOT NULL,applicable boolean NOT NULL,calculated numeric(20,2) NOT NULL,created_by varchar(254) NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),UNIQUE(sale_id,revision,component));
CREATE TABLE penny_margin_events(id bigserial PRIMARY KEY,snapshot_id bigint NOT NULL REFERENCES penny_margin_snapshots(id),kind varchar(20) NOT NULL CHECK(kind IN ('CONFIRMED','ADJUSTED','CANCELLED','SUPERSEDED')),amount numeric(20,2) NOT NULL,reason text NOT NULL,actor varchar(254) NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE penny_margin_rule_audit(id bigserial PRIMARY KEY,rule_id bigint NOT NULL REFERENCES penny_margin_rules(id),action varchar(30) NOT NULL,before_data jsonb NOT NULL,reason text NOT NULL,actor varchar(254) NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX ON penny_margin_snapshots(sale_id,revision);
CREATE INDEX ON penny_margin_events(snapshot_id);
INSERT INTO penny_permissions(code,description) VALUES ('MARGINS_READ','Visualizzare margini e regole'),('MARGINS_WRITE','Gestire margini e regole');
INSERT INTO penny_role_permissions(role_id,permission_code,effect) SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p WHERE r.code IN ('ADMIN','SALES') AND p.code IN ('MARGINS_READ','MARGINS_WRITE');
-- Commercial history is extended with correction events and new rule versions.
CREATE FUNCTION penny_margin_history_immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Margin history is append-only'; END $$;
CREATE TRIGGER margin_snapshots_immutable BEFORE UPDATE OR DELETE ON penny_margin_snapshots FOR EACH ROW EXECUTE FUNCTION penny_margin_history_immutable();
CREATE TRIGGER margin_events_immutable BEFORE UPDATE OR DELETE ON penny_margin_events FOR EACH ROW EXECUTE FUNCTION penny_margin_history_immutable();
CREATE TRIGGER margin_audit_immutable BEFORE UPDATE OR DELETE ON penny_margin_rule_audit FOR EACH ROW EXECUTE FUNCTION penny_margin_history_immutable();
CREATE FUNCTION penny_margin_rule_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Margin rules cannot be deleted'; END IF;
 IF (to_jsonb(NEW)-ARRAY['valid_to','status']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['valid_to','status']) THEN RAISE EXCEPTION 'Create a new rule version'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER margin_rule_versions BEFORE UPDATE OR DELETE ON penny_margin_rules FOR EACH ROW EXECUTE FUNCTION penny_margin_rule_guard();
CREATE TRIGGER margin_profiles_immutable BEFORE UPDATE OR DELETE ON penny_margin_profiles FOR EACH ROW EXECUTE FUNCTION penny_margin_history_immutable();
