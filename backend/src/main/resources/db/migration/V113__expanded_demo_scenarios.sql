-- Existing markers identify the original dataset; actual CRM rows remain untouched.
ALTER TABLE penny_demo_environment ADD COLUMN scenario_version INTEGER NOT NULL DEFAULT 1
 CHECK (scenario_version IN (1,2));
