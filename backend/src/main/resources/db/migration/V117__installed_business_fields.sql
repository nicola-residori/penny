ALTER TABLE penny_installed_products
 ADD COLUMN product_type_id BIGINT REFERENCES penny_product_types(id) ON DELETE RESTRICT,
 ADD COLUMN business JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_installed_business_object CHECK(jsonb_typeof(business)='object');
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_installed_business_enums CHECK (
 (business->>'licenseType' IS NULL OR business->>'licenseType' IN ('PERPETUAL','MONTHLY','QUARTERLY','ANNUAL','THREE_YEAR','TOKEN')) AND
 (business->>'softwareStatus' IS NULL OR business->>'softwareStatus' IN ('ACTIVE','EXPIRING','EXPIRED','SUSPENDED','CANCELLED','OTHER_RESELLER','TO_INSTALL')) AND
 (business->>'hardwareStatus' IS NULL OR business->>'hardwareStatus' IN ('IN_USE','OBSOLETE')) AND
 (business->>'saleSource' IS NULL OR business->>'saleSource' IN ('OUR_SALE','THIRD_PARTY')) AND
 (business->>'saleType' IS NULL OR business->>'saleType' IN ('SALE','RENTAL','DEMO')) AND
 (business->>'licenseType' IS DISTINCT FROM 'PERPETUAL' OR license_expires_on IS NULL) AND
 (business->>'saleType' IS DISTINCT FROM 'SALE' OR business->>'expiresOn' IS NULL) AND
 (coalesce(business->>'saleSource'='OUR_SALE',false) OR (business->>'soldOn' IS NULL AND business->>'salePrice' IS NULL)));
CREATE INDEX penny_installed_type_customer_idx ON penny_installed_products(customer_id,product_type_id) WHERE deleted_at IS NULL;
CREATE FUNCTION penny_installed_type_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE kind TEXT; legacy TEXT; retired BOOLEAN; live BOOLEAN; changed BOOLEAN;
BEGIN
 changed := TG_OP='INSERT';
 IF TG_OP='UPDATE' THEN changed := NEW.product_type_id IS DISTINCT FROM OLD.product_type_id; END IF;
 IF NEW.product_type_id IS NOT NULL THEN
  SELECT c.operational_kind,c.legacy_category,t.obsolete,c.active INTO kind,legacy,retired,live FROM penny_product_types t JOIN penny_product_categories c ON c.id=t.category_id WHERE t.id=NEW.product_type_id FOR SHARE OF t,c;
  IF NOT (kind=NEW.category OR (NEW.category='SERVICE' AND kind='OTHER' AND legacy='SERVICE')) OR (changed AND (retired OR NOT live)) THEN RAISE EXCEPTION 'Incompatible or obsolete installed type' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_installed_type_guard BEFORE INSERT OR UPDATE ON penny_installed_products FOR EACH ROW EXECUTE FUNCTION penny_installed_type_guard();
CREATE FUNCTION penny_installed_allocation_business_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM penny_installed_products WHERE id=NEW.installed_product_id AND (business->>'softwareStatus' IN ('CANCELLED','OTHER_RESELLER') OR business->>'hardwareStatus'='OBSOLETE')) THEN RAISE EXCEPTION 'Installation business status cannot receive allocations' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_installed_allocation_business_guard BEFORE INSERT OR UPDATE ON penny_sale_allocations FOR EACH ROW EXECUTE FUNCTION penny_installed_allocation_business_guard();
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_installed_business_money CHECK (
 (business->>'salePrice' IS NULL OR (business->>'salePrice' ~ '^[0-9]{1,9}(\.[0-9]{1,2})?$' AND (business->>'salePrice')::numeric<=999999999.99)) AND
 (business->>'purchasePrice' IS NULL OR (business->>'purchasePrice' ~ '^[0-9]{1,9}(\.[0-9]{1,2})?$' AND (business->>'purchasePrice')::numeric<=999999999.99)) AND
 (business->>'warrantyYears' IS NULL OR (business->>'warrantyYears' ~ '^[0-9]{1,3}$' AND (business->>'warrantyYears')::integer BETWEEN 0 AND 100)));
CREATE FUNCTION penny_installed_warranty(product_id BIGINT,details JSONB,legacy_date DATE) RETURNS DATE LANGUAGE sql STABLE AS $$
 SELECT CASE WHEN details->>'warrantyYears' IS NULL THEN legacy_date
  ELSE ((CASE WHEN count(s.id)=1 THEN min(s.sold_on) WHEN count(s.id)=0 THEN (details->>'soldOn')::date ELSE NULL END) + make_interval(years=>(details->>'warrantyYears')::integer))::date END
 FROM penny_sale_allocations a JOIN penny_sales s ON s.id=a.sale_id WHERE a.installed_product_id=product_id
$$;

CREATE FUNCTION penny_installed_business_shape_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE k TEXT; v JSONB;
BEGIN
 FOR k,v IN SELECT * FROM jsonb_each(NEW.business) LOOP
  IF k NOT IN ('usageRelease','releaseUpdatedOn','soldRelease','licenseType','softwareStatus','saleSource','soldOn','salePrice','workstationReference','saleType','expiresOn','warrantyYears','purchasePrice','configuration','hardwareStatus') THEN RAISE EXCEPTION 'Unknown installation detail' USING ERRCODE='23514'; END IF;
  IF v='null'::jsonb THEN CONTINUE; END IF;
  IF k='warrantyYears' THEN
   IF jsonb_typeof(v)<>'number' OR (v::text)::numeric<>trunc((v::text)::numeric) THEN RAISE EXCEPTION 'Invalid warranty years' USING ERRCODE='23514'; END IF;
  ELSE
   IF jsonb_typeof(v)<>'string' OR length(NEW.business->>k)>(CASE WHEN k='configuration' THEN 10000 ELSE 240 END) THEN RAISE EXCEPTION 'Invalid installation detail' USING ERRCODE='23514'; END IF;
  END IF;
  IF k IN ('releaseUpdatedOn','soldOn','expiresOn') THEN PERFORM (NEW.business->>k)::date; END IF;
  IF NEW.category<>'SOFTWARE' AND k IN ('usageRelease','releaseUpdatedOn','soldRelease','licenseType','softwareStatus') THEN RAISE EXCEPTION 'Software detail on incompatible installation' USING ERRCODE='23514'; END IF;
  IF NEW.category<>'HARDWARE' AND k IN ('saleType','expiresOn','warrantyYears','purchasePrice','configuration','hardwareStatus') THEN RAISE EXCEPTION 'Hardware detail on incompatible installation' USING ERRCODE='23514'; END IF;
 END LOOP;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_installed_business_shape_guard BEFORE INSERT OR UPDATE OF business,category ON penny_installed_products FOR EACH ROW EXECUTE FUNCTION penny_installed_business_shape_guard();
