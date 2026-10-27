package it.penny.api.contacts;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class SharedContactsMigrationTest {
 @Test void upgrading108RetainsIdentityLinksSitesAndTrash(){
  String schema="m109_preservation";
  String url=System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema="+schema;
  String user=System.getenv("PENNY_TEST_DB_USER"),password=System.getenv("PENNY_TEST_DB_PASSWORD");
  var source=new DriverManagerDataSource(url,user,password);var db=new JdbcTemplate(source);
  db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
  Flyway.configure().dataSource(source).schemas(schema).target("108").load().migrate();
  db.update("INSERT INTO penny_customers(id,kind,name) VALUES (71,'COMPANY','Storico'),(72,'PERSON','Nel cestino'),(73,'COMPANY','Con vendita')");
  db.update("INSERT INTO penny_customer_sites(id,customer_id,name) VALUES (91,71,'Sede storica')");
  db.update("INSERT INTO penny_customer_contacts(id,customer_id,full_name,role,email,is_primary) VALUES (81,71,'Ada','IT','ada@example.test',true),(82,72,'Elena','Buyer',null,false)");
  db.update("UPDATE penny_customer_contacts SET deleted_at=TIMESTAMPTZ '2026-10-01 12:00:00+00' WHERE id=82");
  db.update("UPDATE penny_customers SET deleted_at=TIMESTAMPTZ '2026-10-01 12:00:00+00' WHERE id=72");
  db.update("INSERT INTO penny_trash_entries(entity_type,entity_id,label,deleted_by,deleted_at,expires_at) VALUES ('CONTACT',82,'Elena','migration',TIMESTAMPTZ '2026-10-01 12:00:00+00',TIMESTAMPTZ '2026-10-31 12:00:00+00'),('CUSTOMER',72,'Nel cestino','migration',TIMESTAMPTZ '2026-10-01 12:00:00+00',TIMESTAMPTZ '2026-10-31 12:00:00+00')");
  db.update("INSERT INTO penny_catalog_products(id,code,name,origin,category) VALUES (5,'HIST','Storico','THIRD_PARTY','HARDWARE')");
  db.update("INSERT INTO penny_sales(customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price) VALUES (73,5,'HIST','Storico','THIRD_PARTY','HARDWARE','2026-10-01',1,10)");
  Flyway.configure().dataSource(source).schemas(schema).load().migrate();
  assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_contact_customers",Integer.class));
  assertEquals("IT",db.queryForObject("SELECT role FROM penny_contact_customers WHERE contact_id=81 AND customer_id=71",String.class));
  assertEquals(true,db.queryForObject("SELECT is_primary FROM penny_contact_customers WHERE contact_id=81",Boolean.class));
  assertEquals("Ada",db.queryForObject("SELECT full_name FROM penny_customer_contacts WHERE id=81",String.class));
  assertEquals("Sede storica",db.queryForObject("SELECT name FROM penny_customer_sites WHERE id=91 AND customer_id=71",String.class));
  assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_trash_entries",Integer.class));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customer_contacts WHERE id=82 AND deleted_at=TIMESTAMPTZ '2026-10-01 12:00:00+00'",Integer.class));
  assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_customers WHERE lifecycle_status='PROSPECT'",Integer.class));
  assertEquals("CUSTOMER",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=73",String.class));
 }
}
