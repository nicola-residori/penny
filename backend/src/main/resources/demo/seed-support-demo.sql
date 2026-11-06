-- Additive, repeatable support-call fixtures. Never changes existing business data.
BEGIN;
SET LOCAL TIME ZONE 'Europe/Rome';
SELECT pg_advisory_xact_lock(1122026);
SELECT set_config('penny.actor','demo-support-seed',true);
DO $$
#variable_conflict use_variable
DECLARE
 techs bigint[]; customers bigint[]:=ARRAY[]::bigint[]; contacts bigint[]:=ARRAY[]::bigint[]; sites bigint[]:=ARRAY[]::bigint[];
 names text[]:=ARRAY['Alfa Engineering','Beta Logistica','Studio Gamma','Hotel Delta'];
 cities text[]:=ARRAY['Verona','Vicenza','Mantova','Trento'];
 requests text[]:=ARRAY['Accesso al gestionale bloccato dopo aggiornamento','Stampante di rete non raggiungibile','Configurazione posta elettronica','Lentezza del server applicativo','Ripristino collegamento VPN','Errore esportazione documenti PDF','Aggiornamento licenze software','Verifica backup e spazio disco'];
 marker text:='PENNY_SUPPORT_DEMO_V1';customer_id bigint;site_id bigint;contact_id bigint;record_id bigint;tech bigint;caller bigint;
 i int;j int;ci int;cj int;call_state text;opened timestamptz;closed timestamptz;today_base timestamptz;reason text;reference text;
BEGIN
 SELECT array_agg(u.id ORDER BY u.id) INTO techs FROM penny_users u
 WHERE u.enabled AND NOT u.must_change_password AND
 (SELECT bool_and(coalesce((SELECT p.effect='ALLOW' FROM penny_user_permissions p WHERE p.user_id=u.id AND p.permission_code=req.code),
 EXISTS(SELECT 1 FROM penny_role_permissions p JOIN penny_user_roles ur ON ur.role_id=p.role_id WHERE ur.user_id=u.id AND p.permission_code=req.code AND p.effect='ALLOW')
 AND NOT EXISTS(SELECT 1 FROM penny_role_permissions p JOIN penny_user_roles ur ON ur.role_id=p.role_id WHERE ur.user_id=u.id AND p.permission_code=req.code AND p.effect='DENY')))
 FROM unnest(ARRAY['CUSTOMERS_READ','ACTIVITIES_READ','ACTIVITIES_WRITE']) req(code));
 IF coalesce(array_length(techs,1),0)=0 THEN RAISE EXCEPTION 'Serve almeno un utente attivo abilitato a clienti e attività'; END IF;
 FOR i IN 1..4 LOOP
  SELECT id INTO customer_id FROM penny_customers WHERE notes=marker||':customer:'||i AND deleted_at IS NULL ORDER BY id LIMIT 1;
  IF customer_id IS NULL THEN
   INSERT INTO penny_customers(kind,name,email,phone,city,country,lifecycle_status,notes,created_at)
   VALUES ('COMPANY','[DEMO ASSISTENZA] '||names[i],'demo.assistenza.'||i||'@example.invalid','04500000'||i,cities[i],'IT','PROSPECT',marker||':customer:'||i,date_trunc('month',now())-interval '24 months') RETURNING id INTO customer_id;
  END IF;
  customers:=array_append(customers,customer_id);
  FOR j IN 1..2 LOOP
   SELECT id INTO site_id FROM penny_customer_sites WHERE penny_customer_sites.customer_id=customer_id AND notes=marker||':site:'||j ORDER BY id LIMIT 1;
   IF site_id IS NULL THEN
    INSERT INTO penny_customer_sites(customer_id,name,street,city,site_types,notes)
    VALUES (customer_id,CASE j WHEN 1 THEN '[DEMO] Sede legale e amministrativa' ELSE '[DEMO] Sede operativa' END,'Via Esempio '||i||j,cities[i],CASE j WHEN 1 THEN '["LEGAL","ADMINISTRATIVE"]'::jsonb ELSE '["OPERATIONAL","WAREHOUSE"]'::jsonb END,marker||':site:'||j) RETURNING id INTO site_id;
   END IF;
   sites:=array_append(sites,site_id);
   SELECT id INTO contact_id FROM penny_customer_contacts WHERE email='demo.assistenza.'||i||'.'||j||'@example.invalid' AND deleted_at IS NULL ORDER BY id LIMIT 1;
   IF contact_id IS NULL THEN
    INSERT INTO penny_customer_contacts(customer_id,full_name,email,phone,role,is_primary)
    VALUES (customer_id,'[DEMO] '||CASE j WHEN 1 THEN 'Referente IT ' ELSE 'Referente ufficio ' END||names[i],'demo.assistenza.'||i||'.'||j||'@example.invalid','0450001'||i||j,CASE j WHEN 1 THEN 'Responsabile IT' ELSE 'Amministrazione' END,j=1) RETURNING id INTO contact_id;
    UPDATE penny_contact_customers SET site_id=site_id WHERE penny_contact_customers.contact_id=contact_id AND penny_contact_customers.customer_id=customer_id;
   END IF;
   contacts:=array_append(contacts,contact_id);
  END LOOP;
 END LOOP;
 INSERT INTO penny_contact_customers(contact_id,customer_id,site_id,role) VALUES (contacts[1],customers[2],sites[3],'Consulente IT condiviso') ON CONFLICT DO NOTHING;
 today_base:=greatest(date_trunc('day',now()),now()-interval '6 hours');
 FOR i IN 1..43 LOOP
  IF EXISTS(SELECT 1 FROM penny_customer_records r WHERE r.details->>'demoScenario'=marker AND r.details->>'demoIndex'=i::text) THEN CONTINUE; END IF;
  ci:=((i-1)%4)+1;cj:=(((i-1)/4)%2)+1;tech:=techs[((i-1)%array_length(techs,1))+1];reason:=requests[((i-1)%array_length(requests,1))+1];caller:=contacts[(ci-1)*2+cj];IF i=5 THEN ci:=2;cj:=1;caller:=contacts[1];END IF;
  call_state:=CASE (i-1)%3 WHEN 0 THEN 'RESOLVED' WHEN 1 THEN 'PARTIAL' ELSE 'UNRESOLVED' END;
  IF i<=10 THEN opened:=today_base+(now()-today_base)*(i/12.0);closed:=least(now(),opened+(15+i*3)*interval '1 minute');
  ELSIF i<=16 THEN opened:=date_trunc('day',now())-(i-10)*interval '1 day'+interval '10 hours';closed:=opened+(20+(i%5)*15)*interval '1 minute';
  ELSIF i<=40 THEN opened:=date_trunc('month',now())-(i-16)*interval '1 month'+interval '7 days 10 hours';closed:=opened+(15+(i%6)*20)*interval '1 minute';
  ELSE call_state:='IN_PROGRESS';closed:=NULL;opened:=CASE i WHEN 41 THEN now()-interval '11 minutes' WHEN 42 THEN now()-interval '47 minutes' ELSE date_trunc('day',now())-interval '1 day'+interval '16 hours' END;
  END IF;
  INSERT INTO penny_customer_records(customer_id,site_id,kind,name,status,starts_on,expires_on,notes,details,created_at,updated_at)
  VALUES (customers[ci],sites[(ci-1)*2+cj],'INTERVENTION','[DEMO] Tele-assistenza · '||reason,CASE WHEN call_state='IN_PROGRESS' THEN 'IN_PROGRESS' ELSE 'COMPLETED' END,opened::date,closed::date,'[DEMO] '||reason,
   jsonb_build_object('teleAssistance',true,'interventionType','REMOTE_SUPPORT','venue','ONLINE','contactId',caller,'technicianUserId',tech,'demoScenario',marker,'demoIndex',i),opened,coalesce(closed,opened)) RETURNING id,penny_customer_records.reference INTO record_id,reference;
  INSERT INTO penny_support_calls(record_id,contact_id,requester_name,technician_id,created_by,started_at,ended_at,outcome)
  SELECT record_id,id,full_name,tech,techs[1],opened,closed,call_state FROM penny_customer_contacts WHERE id=caller;
  INSERT INTO penny_support_comments(record_id,author_id,body,created_at)
  VALUES (record_id,tech,'[DEMO] Richiesta registrata e presa in carico dal tecnico.',opened);
  IF closed IS NOT NULL THEN
   INSERT INTO penny_support_comments(record_id,author_id,body,created_at) VALUES (record_id,tech,CASE call_state WHEN 'RESOLVED' THEN '[DEMO] Problema risolto e verificato con il richiedente.' WHEN 'PARTIAL' THEN '[DEMO] Operatività ripristinata parzialmente; pianificare una verifica successiva.' ELSE '[DEMO] Problema non risolto; necessaria un’analisi tecnica aggiuntiva.' END,closed);
  ELSE
   INSERT INTO penny_notifications(user_id,customer_id,support_record_id,message,due_on) VALUES (tech,customers[ci],record_id,'[DEMO] Tele-assistenza '||reference||' assegnata: '||names[ci],opened::date);
  END IF;
 END LOOP;
END $$;
INSERT INTO penny_m2_audit(actor,action,entity_type,entity_id,metadata)
SELECT 'demo-support-seed','SUPPORT_DEMO_SEEDED','ENVIRONMENT',0,jsonb_build_object('calls',count(*)) FROM penny_customer_records WHERE details->>'demoScenario'='PENNY_SUPPORT_DEMO_V1';
SELECT jsonb_build_object('customers',(SELECT count(*) FROM penny_customers WHERE notes LIKE 'PENNY_SUPPORT_DEMO_V1:customer:%'),
 'contacts',(SELECT count(*) FROM penny_customer_contacts WHERE email LIKE 'demo.assistenza.%@example.invalid'),
 'calls',(SELECT count(*) FROM penny_customer_records WHERE details->>'demoScenario'='PENNY_SUPPORT_DEMO_V1'),
 'states',(SELECT jsonb_object_agg(outcome,n) FROM (SELECT s.outcome,count(*) AS n FROM penny_support_calls s JOIN penny_customer_records r ON r.id=s.record_id WHERE r.details->>'demoScenario'='PENNY_SUPPORT_DEMO_V1' GROUP BY s.outcome) counts)) AS demo_summary;
COMMIT;
