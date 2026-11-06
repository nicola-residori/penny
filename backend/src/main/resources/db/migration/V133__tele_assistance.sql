CREATE TABLE penny_support_calls (
 record_id BIGINT PRIMARY KEY REFERENCES penny_customer_records(id) ON DELETE CASCADE,
 contact_id BIGINT REFERENCES penny_customer_contacts(id) ON DELETE SET NULL,requester_name VARCHAR(200) NOT NULL,
 technician_id BIGINT NOT NULL REFERENCES penny_users(id),created_by BIGINT NOT NULL REFERENCES penny_users(id),
 started_at TIMESTAMPTZ NOT NULL DEFAULT now(),ended_at TIMESTAMPTZ,
 outcome VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' CHECK(outcome IN ('IN_PROGRESS','RESOLVED','PARTIAL','UNRESOLVED')),
 CHECK((outcome='IN_PROGRESS' AND ended_at IS NULL) OR (outcome<>'IN_PROGRESS' AND ended_at IS NOT NULL AND ended_at>=started_at))
);
CREATE INDEX penny_support_call_time_idx ON penny_support_calls(started_at DESC);
CREATE INDEX penny_support_call_technician_idx ON penny_support_calls(technician_id,started_at);
CREATE TABLE penny_support_comments (
 id BIGSERIAL PRIMARY KEY,record_id BIGINT NOT NULL REFERENCES penny_support_calls(record_id) ON DELETE CASCADE,
 author_id BIGINT NOT NULL REFERENCES penny_users(id),body TEXT NOT NULL CHECK(length(trim(body)) BETWEEN 1 AND 5000),created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX penny_support_comment_record_idx ON penny_support_comments(record_id,id);
INSERT INTO penny_choices(group_code,code,label) VALUES ('INTERVENTION_TYPE','REMOTE_SUPPORT','Tele-assistenza') ON CONFLICT DO NOTHING;
