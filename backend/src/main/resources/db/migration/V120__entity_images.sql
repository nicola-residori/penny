-- Keep images on their owning rows so trash, restore and permanent deletion
-- follow the existing entity lifecycle, including database backups.
ALTER TABLE penny_customers ADD COLUMN image_data bytea, ADD COLUMN image_mime varchar(32);
ALTER TABLE penny_customer_contacts ADD COLUMN image_data bytea, ADD COLUMN image_mime varchar(32);
ALTER TABLE penny_installed_products ADD COLUMN image_data bytea, ADD COLUMN image_mime varchar(32);

ALTER TABLE penny_customers ADD CONSTRAINT customer_image_valid CHECK (
 (image_data IS NULL AND image_mime IS NULL) OR
 (image_data IS NOT NULL AND image_mime IS NOT NULL AND image_mime IN ('image/png','image/jpeg','image/webp') AND octet_length(image_data) BETWEEN 1 AND 5242880));
ALTER TABLE penny_customer_contacts ADD CONSTRAINT contact_image_valid CHECK (
 (image_data IS NULL AND image_mime IS NULL) OR
 (image_data IS NOT NULL AND image_mime IS NOT NULL AND image_mime IN ('image/png','image/jpeg','image/webp') AND octet_length(image_data) BETWEEN 1 AND 5242880));
ALTER TABLE penny_installed_products ADD CONSTRAINT installed_image_valid CHECK (
 (image_data IS NULL AND image_mime IS NULL) OR
 (image_data IS NOT NULL AND image_mime IS NOT NULL AND image_mime IN ('image/png','image/jpeg','image/webp') AND octet_length(image_data) BETWEEN 1 AND 5242880));
