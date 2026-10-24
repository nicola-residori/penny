CREATE TABLE penny_schema_version (
  id INTEGER PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO penny_schema_version(id) VALUES (1);
