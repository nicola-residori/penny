package it.penny.api.trash;
import it.penny.api.PennyApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=PennyApplication.class,properties={"penny.trash.cleanup-enabled=true","penny.trash.cleanup-interval-ms=100","penny.trash.cleanup-initial-delay-ms=0"})
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class TrashSchedulingTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=trash_scheduler");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"trash_scheduler");}
 @Autowired JdbcTemplate db;
 @Test @Timeout(15) void schedulerAutomaticallyRemovesExpiredRecordsOnly() throws Exception {
  db.execute("TRUNCATE penny_trash_entries,penny_customers CASCADE");
  db.update("INSERT INTO penny_customers(id,kind,name,archived,deleted_at) VALUES (100,'COMPANY','Expired',false,now()-interval '31 days'),(101,'COMPANY','Archived',true,NULL),(102,'COMPANY','Recent',false,now()-interval '5 days')");
  db.update("INSERT INTO penny_trash_entries(entity_type,entity_id,label,deleted_by,deleted_at,expires_at) SELECT 'CUSTOMER',id,name,'test',deleted_at,deleted_at+interval '720 hours' FROM penny_customers WHERE deleted_at IS NOT NULL");
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
  while(System.nanoTime()<deadline&&db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=100",Integer.class)>0)Thread.sleep(50);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=100",Integer.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_customers WHERE id IN (101,102)",Integer.class));
 }
}
