INSERT INTO penny_permissions(code,description) VALUES ('DATA_MANAGE','Caricare demo e azzerare tutti i dati CRM');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT id,'DATA_MANAGE','ALLOW' FROM penny_roles WHERE code='ADMIN';
CREATE TABLE penny_demo_environment (
 id INTEGER PRIMARY KEY CHECK(id=1), loaded_at TIMESTAMPTZ NOT NULL DEFAULT now(), actor VARCHAR(320) NOT NULL
);
