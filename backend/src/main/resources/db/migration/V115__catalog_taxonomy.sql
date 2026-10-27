CREATE TABLE penny_product_categories (
 id BIGSERIAL PRIMARY KEY,code VARCHAR(40) NOT NULL UNIQUE,name VARCHAR(160) NOT NULL CHECK(length(trim(name))>0),
 operational_kind VARCHAR(16) NOT NULL CHECK(operational_kind IN ('HARDWARE','SOFTWARE','TRAINING','CONTRACT','OTHER')),
 legacy_category VARCHAR(12) NOT NULL CHECK(legacy_category IN ('HARDWARE','SOFTWARE','TRAINING','SERVICE')),
 active BOOLEAN NOT NULL DEFAULT true,is_system BOOLEAN NOT NULL DEFAULT false,
 UNIQUE(id,legacy_category),CHECK(legacy_category=CASE WHEN operational_kind IN ('CONTRACT','OTHER') THEN 'SERVICE' ELSE operational_kind END)
);
CREATE UNIQUE INDEX penny_category_name_unique ON penny_product_categories(lower(name));
INSERT INTO penny_product_categories(code,name,operational_kind,legacy_category,is_system) VALUES
 ('HARDWARE','Hardware','HARDWARE','HARDWARE',true),('SOFTWARE','Software','SOFTWARE','SOFTWARE',true),
 ('TRAINING','Formazione','TRAINING','TRAINING',true),('CONTRACT','Contratto','CONTRACT','SERVICE',true),('SERVICE','Servizi','OTHER','SERVICE',true);
CREATE TABLE penny_product_types (
 id BIGSERIAL PRIMARY KEY,category_id BIGINT NOT NULL REFERENCES penny_product_categories(id) ON DELETE RESTRICT,
 name VARCHAR(160) NOT NULL CHECK(length(trim(name))>0),obsolete BOOLEAN NOT NULL DEFAULT false,is_system BOOLEAN NOT NULL DEFAULT false,
 UNIQUE(id,category_id)
);
CREATE UNIQUE INDEX penny_type_name_unique ON penny_product_types(category_id,lower(name));
INSERT INTO penny_product_types(category_id,name,is_system)
 SELECT c.id,v.name,true FROM (VALUES
 ('HARDWARE','Workstation'),('HARDWARE','Workstation mobile'),('HARDWARE','Monitor'),('HARDWARE','Stampanti & Plotter'),('HARDWARE','3DConnexion'),
 ('SOFTWARE','Autodesk'),('SOFTWARE','Altri CAD'),('SOFTWARE','QS Informatica'),('SOFTWARE','Office'),('SOFTWARE','Altros'),
 ('TRAINING','Corso standard'),('TRAINING','Corso personalizzato'),('TRAINING','OTC'),('TRAINING','Corso finanziato'),
 ('CONTRACT','Contratto di Assistenza'),('CONTRACT','Pacchetto orario'),('CONTRACT','Contratto Spac'),('CONTRACT','Contratto Office')) v(code,name)
 JOIN penny_product_categories c ON c.code=v.code;
ALTER TABLE penny_catalog_products ADD COLUMN category_id BIGINT,ADD COLUMN product_type_id BIGINT,
 ADD COLUMN unit VARCHAR(40),ADD COLUMN supply_channel VARCHAR(20) CHECK(supply_channel IN ('INTERNAL_SALE','EXTERNAL_SUPPLY')),
 ADD COLUMN image_data BYTEA,ADD COLUMN image_mime VARCHAR(20),
 ADD CONSTRAINT penny_catalog_image_valid CHECK((image_data IS NULL AND image_mime IS NULL) OR (image_data IS NOT NULL AND image_mime IN ('image/png','image/jpeg','image/webp') AND octet_length(image_data)<=5242880));
ALTER TABLE penny_catalog_products DISABLE TRIGGER penny_catalog_trash_write_guard;
UPDATE penny_catalog_products p SET category_id=c.id FROM penny_product_categories c WHERE c.code=p.category;
ALTER TABLE penny_catalog_products ENABLE TRIGGER penny_catalog_trash_write_guard;
ALTER TABLE penny_catalog_products ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE penny_catalog_products ADD CONSTRAINT penny_catalog_category_business FOREIGN KEY(category_id,category) REFERENCES penny_product_categories(id,legacy_category) ON UPDATE RESTRICT ON DELETE RESTRICT,
 ADD CONSTRAINT penny_catalog_type_business FOREIGN KEY(product_type_id,category_id) REFERENCES penny_product_types(id,category_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
CREATE FUNCTION penny_default_catalog_category() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.category_id IS NULL THEN SELECT id INTO NEW.category_id FROM penny_product_categories WHERE code=NEW.category; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_catalog_category_default BEFORE INSERT ON penny_catalog_products FOR EACH ROW EXECUTE FUNCTION penny_default_catalog_category();
CREATE FUNCTION penny_taxonomy_identity_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_TABLE_NAME='penny_product_categories' THEN
  IF NEW.operational_kind IS DISTINCT FROM OLD.operational_kind OR NEW.legacy_category IS DISTINCT FROM OLD.legacy_category OR NEW.code IS DISTINCT FROM OLD.code THEN
   RAISE EXCEPTION 'Operational category identity is immutable' USING ERRCODE='23514';
  END IF;
 ELSE
  IF NEW.category_id IS DISTINCT FROM OLD.category_id THEN RAISE EXCEPTION 'Product type cannot change category' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_category_identity_guard BEFORE UPDATE ON penny_product_categories FOR EACH ROW EXECUTE FUNCTION penny_taxonomy_identity_guard();
CREATE TRIGGER penny_type_identity_guard BEFORE UPDATE ON penny_product_types FOR EACH ROW EXECUTE FUNCTION penny_taxonomy_identity_guard();
CREATE FUNCTION penny_check_sale_taxonomy() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE available boolean;type_id bigint;is_obsolete boolean;
BEGIN
 IF NEW.status='CONFIRMED' AND (TG_OP='INSERT' OR NEW.catalog_product_id IS DISTINCT FROM OLD.catalog_product_id OR OLD.status IS DISTINCT FROM NEW.status) THEN
  SELECT (p.active AND p.deleted_at IS NULL AND c.active),p.product_type_id INTO available,type_id FROM penny_catalog_products p JOIN penny_product_categories c ON c.id=p.category_id WHERE p.id=NEW.catalog_product_id FOR SHARE OF p,c;
  IF type_id IS NOT NULL THEN SELECT obsolete INTO is_obsolete FROM penny_product_types WHERE id=type_id FOR SHARE; END IF;
  IF NOT coalesce(available,false) OR coalesce(is_obsolete,false) THEN RAISE EXCEPTION 'Catalog category or product type is obsolete' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER penny_sales_taxonomy_guard BEFORE INSERT OR UPDATE OF catalog_product_id,status ON penny_sales FOR EACH ROW EXECUTE FUNCTION penny_check_sale_taxonomy();
