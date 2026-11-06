package it.penny.api.support;
import it.penny.api.PennyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=PennyApplication.class,properties="penny.trash.cleanup-enabled=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class SupportDemoSeedTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=support_demo_seed");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"support_demo_seed");}
 @Autowired JdbcTemplate db;
 @Test void addsCompleteDemoWithoutTouchingExistingDataAndCanRunTwice()throws Exception{
  db.execute("TRUNCATE penny_customers,penny_users CASCADE");db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES ('demo-seed@test.invalid','Demo technician','unused',false)");db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email='demo-seed@test.invalid' AND r.code='ADMIN'");long original=db.queryForObject("INSERT INTO penny_customers(kind,name,notes) VALUES ('COMPANY','Da conservare','originale') RETURNING id",Long.class);
  String sql=new ClassPathResource("demo/seed-support-demo.sql").getContentAsString(StandardCharsets.UTF_8);
  db.execute(sql);assertEquals(4L,db.queryForObject("SELECT count(*) FROM penny_customers WHERE notes LIKE 'PENNY_SUPPORT_DEMO_V1:customer:%'",Long.class));assertEquals(43L,db.queryForObject("SELECT count(*) FROM penny_support_calls",Long.class));assertEquals(3L,db.queryForObject("SELECT count(*) FROM penny_support_calls WHERE outcome='IN_PROGRESS'",Long.class));assertEquals(4L,db.queryForObject("SELECT count(DISTINCT outcome) FROM penny_support_calls",Long.class));assertEquals(8L,db.queryForObject("SELECT count(*) FROM penny_customer_contacts",Long.class));assertEquals(8L,db.queryForObject("SELECT count(*) FROM penny_customer_sites",Long.class));assertEquals(3L,db.queryForObject("SELECT count(*) FROM penny_notifications WHERE support_record_id IS NOT NULL AND status='UNREAD'",Long.class));assertEquals(0L,db.queryForObject("SELECT count(*) FROM penny_support_calls s JOIN penny_customer_records r ON r.id=s.record_id WHERE NOT EXISTS(SELECT 1 FROM penny_contact_customers l WHERE l.contact_id=s.contact_id AND l.customer_id=r.customer_id)",Long.class));assertTrue(db.queryForObject("SELECT count(DISTINCT extract(year FROM started_at))>=2 FROM penny_support_calls",Boolean.class));String coverage=new ClassPathResource("demo/seed-support-coverage.sql").getContentAsString(StandardCharsets.UTF_8);db.execute(coverage);db.execute(coverage);assertEquals(3L,db.queryForObject("SELECT count(*) FROM penny_customer_records WHERE details->>'demoScenario'='PENNY_SUPPORT_COVERAGE_DEMO_V1'",Long.class));db.execute(sql);assertEquals(43L,db.queryForObject("SELECT count(*) FROM penny_support_calls",Long.class));assertEquals(3L,db.queryForObject("SELECT count(*) FROM penny_notifications WHERE support_record_id IS NOT NULL",Long.class));assertEquals("originale",db.queryForObject("SELECT notes FROM penny_customers WHERE id=?",String.class,original));
 }
}
