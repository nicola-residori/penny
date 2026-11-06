ALTER TABLE penny_notifications ADD COLUMN support_record_id BIGINT REFERENCES penny_support_calls(record_id) ON DELETE CASCADE;
CREATE UNIQUE INDEX penny_notification_support_assignment ON penny_notifications(user_id,support_record_id) WHERE support_record_id IS NOT NULL;
-- Notify technicians for calls already running when this feature is installed.
INSERT INTO penny_notifications(user_id,customer_id,support_record_id,message,due_on)
 SELECT s.technician_id,r.customer_id,s.record_id,'Tele-assistenza '||r.reference||' assegnata: '||c.name||' · '||s.requester_name,(s.started_at AT TIME ZONE 'Europe/Rome')::date
 FROM penny_support_calls s JOIN penny_customer_records r ON r.id=s.record_id JOIN penny_customers c ON c.id=r.customer_id
 WHERE s.outcome='IN_PROGRESS' AND r.deleted_at IS NULL AND c.deleted_at IS NULL
 ON CONFLICT(user_id,support_record_id) WHERE support_record_id IS NOT NULL DO NOTHING;
