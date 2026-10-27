CREATE TABLE penny_customer_sites (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES penny_customers(id) ON DELETE CASCADE,
 name VARCHAR(200) NOT NULL CHECK (length(trim(name))>0),
 street VARCHAR(240), city VARCHAR(120), notes TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX penny_customer_sites_customer_idx ON penny_customer_sites(customer_id,id);
ALTER TABLE penny_installed_products ADD COLUMN site_id BIGINT REFERENCES penny_customer_sites(id) ON DELETE RESTRICT;
ALTER TABLE penny_workstations ADD COLUMN site_id BIGINT REFERENCES penny_customer_sites(id) ON DELETE RESTRICT;
CREATE INDEX penny_installed_products_site_idx ON penny_installed_products(site_id);
CREATE INDEX penny_workstations_site_idx ON penny_workstations(site_id);
CREATE FUNCTION penny_check_site_customer() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.site_id IS NOT NULL THEN
  PERFORM 1 FROM penny_customer_sites s WHERE s.id=NEW.site_id AND s.customer_id=NEW.customer_id FOR SHARE;
  IF NOT FOUND THEN
  RAISE EXCEPTION 'Site must belong to the same customer' USING ERRCODE='23514';
 END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_installed_product_site_customer BEFORE INSERT OR UPDATE OF site_id,customer_id ON penny_installed_products FOR EACH ROW EXECUTE FUNCTION penny_check_site_customer();
CREATE TRIGGER penny_workstation_site_customer BEFORE INSERT OR UPDATE OF site_id,customer_id ON penny_workstations FOR EACH ROW EXECUTE FUNCTION penny_check_site_customer();
CREATE FUNCTION penny_check_site_customer_move() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS (SELECT 1 FROM penny_installed_products WHERE site_id=NEW.id AND customer_id<>NEW.customer_id) OR EXISTS (SELECT 1 FROM penny_workstations WHERE site_id=NEW.id AND customer_id<>NEW.customer_id) THEN
  RAISE EXCEPTION 'Linked site cannot change customer' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_site_customer_move BEFORE UPDATE OF customer_id ON penny_customer_sites FOR EACH ROW EXECUTE FUNCTION penny_check_site_customer_move();

CREATE FUNCTION penny_check_site_live_customer() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM 1 FROM penny_customers WHERE id=NEW.customer_id AND deleted_at IS NULL FOR SHARE;
 IF NOT FOUND THEN
  RAISE EXCEPTION 'Site requires a live customer' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_site_live_customer BEFORE INSERT OR UPDATE ON penny_customer_sites FOR EACH ROW EXECUTE FUNCTION penny_check_site_live_customer();
