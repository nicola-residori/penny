# Expanded demo backend implementation plan

**Goal:** Incrementally upgrade base demo markers to version 2 with substantial recognizable scenarios.
**Architecture:** Preserve base seeding and add a dedicated JdbcTemplate seeder inside the existing transaction and advisory lock. V113 defaults existing markers to version 1; absent marker means version 0.
**Tech Stack:** Java 21, Spring MVC/JDBC, PostgreSQL, Flyway.
**Spec:** docs/specifiche-funzionali.md and current delegated expanded-demo requirements.

## Constraints
Never reset or modify existing CRM/security rows. New records use [DEMO] E2 names and E2 codes. Sales alone promote prospects; TRAINING is never installed. No live execution or commits by this worker.

## Steps
- [x] Add integration test for legacy upgrade, untouched real row, volume, pagination and repeat no-op.
- [x] Parent runs targeted test and confirms RED on missing scenarioVersion.
- [x] Add V113 scenario_version default 1; expose scenarioVersion/demoUpgradeAvailable.
- [x] Add ExpandedDemoScenarioSeeder with 50 customers, multisite/shared contacts, 40 catalog entries, installations, sales and nonexpired recoverable trash.
- [ ] Parent runs backend tests; resolve failures and document exact samples/counts.

## Review focus
Legacy marker upgrade adds records without changing earlier rows; fresh load retains base cases. Repeat version 2 calls do not insert. Shared identity links retain same-customer sites. Allocations use only confirmed sales and live nonretired installations. Trash includes only deletable, unallocated objects and retains exact 720 hour policy.
