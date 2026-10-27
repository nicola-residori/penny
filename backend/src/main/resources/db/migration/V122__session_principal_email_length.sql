-- Session principals are user email addresses, whose existing limit is 254.
ALTER TABLE SPRING_SESSION ALTER COLUMN PRINCIPAL_NAME TYPE VARCHAR(254);
