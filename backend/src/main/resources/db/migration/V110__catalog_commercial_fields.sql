-- Existing unknown prices stay NULL; manufacturer and supplier are independent.
ALTER TABLE penny_catalog_products ADD COLUMN supplier VARCHAR(120);
ALTER TABLE penny_catalog_products ADD COLUMN price NUMERIC(11,2);
ALTER TABLE penny_catalog_products ADD CONSTRAINT penny_catalog_price_nonnegative CHECK (price >= 0 AND price <= 999999999.99);
-- Locate the original generated category constraint by its constrained column.
DO $$
DECLARE constraint_name TEXT;
BEGIN
 FOR constraint_name IN
  SELECT c.conname FROM pg_constraint c
  JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='category'
  WHERE c.conrelid='penny_catalog_products'::regclass AND c.contype='c' AND c.conkey=ARRAY[a.attnum]::smallint[]
 LOOP
  EXECUTE format('ALTER TABLE penny_catalog_products DROP CONSTRAINT %I',constraint_name);
 END LOOP;
END $$;
ALTER TABLE penny_catalog_products ADD CONSTRAINT penny_catalog_category CHECK (category IN ('HARDWARE','SOFTWARE','SERVICE','TRAINING'));
-- The installed-products category constraint deliberately remains unchanged.
