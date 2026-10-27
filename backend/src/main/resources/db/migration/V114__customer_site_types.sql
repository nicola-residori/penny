-- Existing sites remain unclassified; no type is inferred from their names.
CREATE FUNCTION penny_valid_site_types(types jsonb) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT AS $$
DECLARE value jsonb; seen text[] := ARRAY[]::text[]; code text;
BEGIN
 IF jsonb_typeof(types) <> 'array' THEN RETURN false; END IF;
 IF jsonb_array_length(types) > 5 THEN RETURN false; END IF;
 FOR value IN SELECT jsonb_array_elements(types) LOOP
  IF jsonb_typeof(value) <> 'string' THEN RETURN false; END IF;
  code := value #>> '{}';
  IF code NOT IN ('LEGAL','ADMINISTRATIVE','OPERATIONAL','COMMERCIAL','WAREHOUSE') OR code = ANY(seen) THEN RETURN false; END IF;
  seen := array_append(seen,code);
 END LOOP;
 RETURN true;
END $$;
ALTER TABLE penny_customer_sites ADD COLUMN site_types jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE penny_customer_sites ADD CONSTRAINT penny_customer_sites_types_valid CHECK (penny_valid_site_types(site_types));
