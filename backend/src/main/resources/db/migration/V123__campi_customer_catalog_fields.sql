-- Campi.pdf: retain legacy names and values while exposing structured contact data.
DROP TRIGGER IF EXISTS penny_customer_lifecycle_guard ON penny_customers;
ALTER TABLE penny_customer_contacts
 ADD COLUMN first_name varchar(200), ADD COLUMN last_name varchar(200),
 ADD COLUMN company_mobile varchar(40), ADD COLUMN personal_mobile varchar(40),
 ADD COLUMN contact_status varchar(120) NOT NULL DEFAULT 'ACTIVE',
 ADD COLUMN contact_reference varchar(120);
ALTER TABLE penny_catalog_products
 ADD COLUMN warranty_years integer CHECK(warranty_years BETWEEN 0 AND 100),
 ADD COLUMN margin_management varchar(20) NOT NULL DEFAULT 'NONE' CHECK(margin_management IN ('NONE','AUTOMATIC')),
 ADD COLUMN margin_profile_id bigint;

-- Choice values are configurable; retain provenance and date invariants.
ALTER TABLE penny_installed_products DROP CONSTRAINT penny_installed_business_enums;
ALTER TABLE penny_installed_products ADD CONSTRAINT penny_installed_business_enums CHECK (
 (business->>'saleSource' IS NULL OR business->>'saleSource' IN ('OUR_SALE','THIRD_PARTY')) AND
 (business->>'licenseType' IS DISTINCT FROM 'PERPETUAL' OR license_expires_on IS NULL) AND
 (business->>'saleType' NOT IN ('SALE','POST_LEASING') OR business->>'expiresOn' IS NULL) AND
 (coalesce(business->>'saleSource'='OUR_SALE',false) OR (business->>'soldOn' IS NULL AND business->>'salePrice' IS NULL)));
CREATE OR REPLACE FUNCTION penny_installed_business_shape_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE k TEXT; v JSONB;
BEGIN
 FOR k,v IN SELECT * FROM jsonb_each(NEW.business) LOOP
  IF k NOT IN ('usageRelease','releaseUpdatedOn','soldRelease','licenseType','softwareStatus','saleSource','soldOn','salePrice','workstationReference','saleType','expiresOn','warrantyYears','purchasePrice','configuration','hardwareStatus','softwareKeyId','subscriptionId','contractNumber','softwareSaleType','invoiceReference','contactId','marginRuleId') THEN RAISE EXCEPTION 'Unknown installation detail' USING ERRCODE='23514'; END IF;
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
