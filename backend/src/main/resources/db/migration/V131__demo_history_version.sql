-- Extend the additive demo scenario marker without changing existing loaded versions.
ALTER TABLE penny_demo_environment
 DROP CONSTRAINT penny_demo_environment_scenario_version_check;
ALTER TABLE penny_demo_environment
 ADD CONSTRAINT penny_demo_environment_scenario_version_check
 CHECK (scenario_version IN (1,2,3));
