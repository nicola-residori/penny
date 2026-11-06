BEGIN;
SET LOCAL TIME ZONE 'Europe/Rome';
SELECT pg_advisory_xact_lock(1122026);
INSERT INTO penny_customer_records(customer_id,kind,name,status,starts_on,expires_on,notes,details)
 SELECT c.id,'CONTRACT','[DEMO] Assistenza telefonica · '||v.label,v.status,current_date-365,current_date+v.days,
 '[DEMO] Contratto di prova per verificare gli avvisi in tele-assistenza.',
 jsonb_build_object('contractType','PHONE','assistanceBase','GENERAL','demoScenario','PENNY_SUPPORT_COVERAGE_DEMO_V1')
 FROM penny_customers c JOIN (VALUES
 ('PENNY_SUPPORT_DEMO_V1:customer:1','attiva','ACTIVE',365),
 ('PENNY_SUPPORT_DEMO_V1:customer:2','scaduta','EXPIRED',-14),
 ('PENNY_SUPPORT_DEMO_V1:customer:4','sospesa','SUSPENDED',90)
 ) AS v(marker,label,status,days) ON c.notes=v.marker
 WHERE c.deleted_at IS NULL AND NOT EXISTS(SELECT 1 FROM penny_customer_records r WHERE r.customer_id=c.id AND r.details->>'demoScenario'='PENNY_SUPPORT_COVERAGE_DEMO_V1');
SELECT count(*) AS demo_contracts FROM penny_customer_records WHERE details->>'demoScenario'='PENNY_SUPPORT_COVERAGE_DEMO_V1';
COMMIT;
