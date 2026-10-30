CREATE TABLE penny_sales_orders (
 id BIGSERIAL PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES penny_customers(id),
 order_number VARCHAR(200) NOT NULL,
 sold_on DATE NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','CONFIRMED','CANCELLED')),
 notes TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(id,customer_id)
);
ALTER TABLE penny_sales ADD COLUMN order_id BIGINT;
ALTER TABLE penny_sales ADD COLUMN business JSONB NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(business)='object');
ALTER TABLE penny_sales ADD CONSTRAINT penny_sales_order_customer_fk FOREIGN KEY(order_id,customer_id) REFERENCES penny_sales_orders(id,customer_id);
-- Preserve ledger identities and every historical allocation/margin reference.
INSERT INTO penny_sales_orders(id,customer_id,order_number,sold_on,status,notes,created_at,updated_at)
 SELECT id,customer_id,coalesce(nullif(reference,''),'LEGACY-'||id),sold_on,status,notes,created_at,updated_at FROM penny_sales;
UPDATE penny_sales SET order_id=id;
SELECT setval(pg_get_serial_sequence('penny_sales_orders','id'), greatest(coalesce((SELECT max(id) FROM penny_sales_orders),0),1), EXISTS(SELECT 1 FROM penny_sales_orders));
CREATE INDEX penny_sales_order_idx ON penny_sales(order_id);
ALTER TABLE penny_installed_products ADD COLUMN sale_id BIGINT REFERENCES penny_sales(id);
ALTER TABLE penny_installed_products ADD COLUMN sale_unit INTEGER;
CREATE UNIQUE INDEX penny_installed_sale_unit_unique ON penny_installed_products(sale_id,sale_unit) WHERE sale_id IS NOT NULL;

ALTER TABLE penny_sales DROP CONSTRAINT penny_sales_status_check;
ALTER TABLE penny_sales ADD CONSTRAINT penny_sales_status_check CHECK(status IN ('DRAFT','CONFIRMED','CANCELLED'));
