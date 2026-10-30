CREATE TABLE penny_documents (
 id BIGSERIAL PRIMARY KEY,customer_id BIGINT NOT NULL REFERENCES penny_customers(id),sale_order_id BIGINT,
 installed_product_id BIGINT REFERENCES penny_installed_products(id),catalog_product_id BIGINT REFERENCES penny_catalog_products(id),record_id BIGINT REFERENCES penny_customer_records(id),
 filename VARCHAR(200) NOT NULL,mime_type VARCHAR(100) NOT NULL,content BYTEA NOT NULL CHECK(octet_length(content) BETWEEN 1 AND 5242880),
 uploaded_by BIGINT NOT NULL REFERENCES penny_users(id),deleted_at TIMESTAMPTZ,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(sale_order_id,customer_id) REFERENCES penny_sales_orders(id,customer_id)
);
CREATE INDEX penny_documents_customer_idx ON penny_documents(customer_id,id);
CREATE TABLE penny_notifications (
 id BIGSERIAL PRIMARY KEY,user_id BIGINT NOT NULL REFERENCES penny_users(id),customer_id BIGINT REFERENCES penny_customers(id),
 message VARCHAR(2000) NOT NULL CHECK(length(trim(message))>0),due_on DATE NOT NULL,
 status VARCHAR(12) NOT NULL DEFAULT 'UNREAD' CHECK(status IN ('UNREAD','READ','CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX penny_notifications_owner_due_idx ON penny_notifications(user_id,due_on,status);
INSERT INTO penny_permissions(code,description) VALUES
 ('DOCUMENTS_READ','Visualizzare e scaricare documenti privati'),('DOCUMENTS_WRITE','Caricare ed eliminare documenti privati'),
 ('NOTIFICATIONS_READ','Visualizzare i propri promemoria'),('NOTIFICATIONS_WRITE','Gestire i propri promemoria');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
 SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
 WHERE r.code IN ('ADMIN','SALES') AND p.code IN ('DOCUMENTS_READ','DOCUMENTS_WRITE','NOTIFICATIONS_READ','NOTIFICATIONS_WRITE');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
 SELECT r.id,p.code,'ALLOW' FROM penny_roles r CROSS JOIN penny_permissions p
 WHERE r.code='TECH' AND p.code IN ('NOTIFICATIONS_READ','NOTIFICATIONS_WRITE');
