INSERT INTO penny_permissions(code,description) VALUES ('TRASH_PURGE','Svuotare definitivamente il cestino');
INSERT INTO penny_role_permissions(role_id,permission_code,effect)
SELECT id,'TRASH_PURGE','ALLOW' FROM penny_roles WHERE code='ADMIN';
