-- Additive storage: existing customer/document records are unchanged.
CREATE TABLE penny_mail_connections (
 id BIGSERIAL PRIMARY KEY,user_id BIGINT NOT NULL REFERENCES penny_users(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL UNIQUE,session_version BIGINT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),expires_at TIMESTAMPTZ NOT NULL DEFAULT now()+interval '30 days',revoked_at TIMESTAMPTZ
);
CREATE TABLE penny_mail_messages (
 id BIGSERIAL PRIMARY KEY,customer_id BIGINT NOT NULL REFERENCES penny_customers(id) ON DELETE CASCADE,
 message_key VARCHAR(64) NOT NULL,internet_message_id VARCHAR(1000),conversation_key VARCHAR(64) NOT NULL,
 subject VARCHAR(1000) NOT NULL,sender VARCHAR(320) NOT NULL,recipients TEXT NOT NULL,sent_at TIMESTAMPTZ NOT NULL,
 body_text TEXT NOT NULL CHECK(length(body_text)<=1000000),
 original_name VARCHAR(200) NOT NULL,original_data BYTEA NOT NULL CHECK(octet_length(original_data) BETWEEN 1 AND 20971520),
 original_kind VARCHAR(12) NOT NULL CHECK(original_kind IN ('EML','MSG','GENERATED')),
 saved_by BIGINT NOT NULL REFERENCES penny_users(id),saved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(customer_id,message_key)
);
CREATE INDEX penny_mail_customer_date_idx ON penny_mail_messages(customer_id,sent_at DESC,id DESC);
CREATE INDEX penny_mail_conversation_idx ON penny_mail_messages(customer_id,conversation_key,sent_at);
CREATE TABLE penny_mail_attachments (
 id BIGSERIAL PRIMARY KEY,message_id BIGINT NOT NULL REFERENCES penny_mail_messages(id) ON DELETE CASCADE,
 filename VARCHAR(200) NOT NULL,content BYTEA NOT NULL CHECK(octet_length(content)<=10485760),inline_image BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX penny_mail_attachment_message_idx ON penny_mail_attachments(message_id);
ALTER TABLE penny_mail_messages ADD CONSTRAINT penny_mail_customer_identity UNIQUE(id,customer_id);
ALTER TABLE penny_customer_records ADD CONSTRAINT penny_record_mail_customer_identity UNIQUE(id,customer_id);
CREATE TABLE penny_mail_record_links (
 message_id BIGINT NOT NULL,record_id BIGINT NOT NULL,customer_id BIGINT NOT NULL,
 FOREIGN KEY(message_id,customer_id) REFERENCES penny_mail_messages(id,customer_id) ON DELETE CASCADE,
 FOREIGN KEY(record_id,customer_id) REFERENCES penny_customer_records(id,customer_id) ON DELETE CASCADE,
 linked_by BIGINT NOT NULL REFERENCES penny_users(id),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY(message_id,record_id)
);
CREATE INDEX penny_mail_record_idx ON penny_mail_record_links(record_id,message_id);
CREATE TABLE penny_mail_comments (
 id BIGSERIAL PRIMARY KEY,message_id BIGINT NOT NULL REFERENCES penny_mail_messages(id) ON DELETE CASCADE,
 author_id BIGINT NOT NULL REFERENCES penny_users(id),body TEXT NOT NULL CHECK(length(trim(body)) BETWEEN 1 AND 5000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
