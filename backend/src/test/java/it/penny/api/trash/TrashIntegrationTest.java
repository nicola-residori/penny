package it.penny.api.trash;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.penny.api.PennyApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(classes=PennyApplication.class,properties="penny.trash.cleanup-enabled=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class TrashIntegrationTest {
 static final String URL=System.getenv("PENNY_TEST_JDBC_URL"),USER=System.getenv("PENNY_TEST_DB_USER"),PASSWORD=System.getenv("PENNY_TEST_DB_PASSWORD");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->URL+"?currentSchema=trash_api");p.add("spring.datasource.username",()->USER);p.add("spring.datasource.password",()->PASSWORD);p.add("spring.flyway.schemas",()->"trash_api");p.add("spring.jpa.hibernate.ddl-auto",()->"validate");}
 @Autowired JdbcTemplate db;@Autowired WebApplicationContext context;@Autowired ObjectMapper json;MockMvc mvc;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("trash-admin@test.invalid","unused"),tech=new UsernamePasswordAuthenticationToken("trash-tech@test.invalid","unused");
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).build();
  db.execute("TRUNCATE penny_trash_entries,penny_sales,penny_workstations,penny_installed_products,penny_catalog_products,penny_customers,penny_users,penny_m2_audit RESTART IDENTITY CASCADE");
  for(String role:new String[]{"ADMIN","TECH"}){String email="trash-"+role.toLowerCase()+"@test.invalid";db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,?,'unused',false)",email,role);db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code=?",email,role);}
  db.update("INSERT INTO penny_customers(id,kind,name,archived) VALUES (1,'COMPANY','Aurora',true),(2,'COMPANY','Beta',false)");
  db.update("INSERT INTO penny_catalog_products(id,code,name,origin,category) VALUES (1,'PEN','Penny','INTERNAL','SOFTWARE')");
 }
 long trash(String type,long id) throws Exception {mvc.perform(delete("/api/trash/"+type+"/"+id).principal(admin)).andExpect(status().isOk());return db.queryForObject("SELECT id FROM penny_trash_entries WHERE entity_type=? AND entity_id=?",Long.class,type,id);}
 void age(long entry,int days){db.update("UPDATE penny_trash_entries SET deleted_at=now()-(? * interval '1 day'),expires_at=now()-((?-30) * interval '1 day') WHERE id=?",days,days,entry);}
 void purge() throws Exception {var service=context.getBean("trashService");service.getClass().getMethod("purgeExpired").invoke(service);}
 @Test void customerArchiveRemainsSeparateAndRestorePreservesState() throws Exception {
  long entry=trash("CUSTOMER",1);
  mvc.perform(get("/api/customers").principal(admin).param("archived","true")).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/api/customers/1").principal(admin)).andExpect(status().isNotFound());
  mvc.perform(get("/api/trash").principal(admin)).andExpect(jsonPath("$.items[0].label").value("Aurora")).andExpect(jsonPath("$.items[0].daysRemaining").value(30)).andExpect(jsonPath("$.items[0].canRestore").value(true));
  mvc.perform(post("/api/trash/"+entry+"/restore").principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/customers/1").principal(admin)).andExpect(jsonPath("$.archived").value(true));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_trash_entries",Integer.class));
  assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_m2_audit",Integer.class));
 }
 @Test void thirtyDayCutoffAndExpiredRestore() throws Exception {
  long early=trash("CUSTOMER",1),expired=trash("CUSTOMER",2);age(early,29);age(expired,30);
  mvc.perform(post("/api/trash/"+expired+"/restore").principal(admin)).andExpect(status().isGone());
  purge();assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=1",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=2",Integer.class));
  mvc.perform(post("/api/trash/"+early+"/restore").principal(admin)).andExpect(status().isOk());
 }
 @Test void linkedRecordsCannotEnterTrashAndGuardsPreventNewLinks() throws Exception {
  db.update("INSERT INTO penny_installed_products(id,customer_id,name,category,origin,catalog_product_id) VALUES (1,2,'Suite','SOFTWARE','INTERNAL',1)");
  mvc.perform(delete("/api/trash/CUSTOMER/2").principal(admin)).andExpect(status().isConflict());mvc.perform(delete("/api/trash/CATALOG_PRODUCT/1").principal(admin)).andExpect(status().isConflict());
  trash("CUSTOMER",1);
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_workstations(customer_id,name) VALUES (1,'Non valida')"));
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Nascosto')"));
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_catalog_products SET deleted_at=now() WHERE id=1"));
 }
 @Test void contactsAreSoftDeletedAndFollowDeletedCustomer() throws Exception {
  db.update("INSERT INTO penny_customer_contacts(id,customer_id,full_name) VALUES (7,1,'Ada')");
  mvc.perform(delete("/api/trash/CONTACT/7").principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/customers/1/contacts").principal(admin)).andExpect(jsonPath("$.length()").value(0));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customer_contacts WHERE id=7",Integer.class));
  long contact=db.queryForObject("SELECT id FROM penny_trash_entries WHERE entity_type='CONTACT'",Long.class);
  long customer=trash("CUSTOMER",1);
  mvc.perform(post("/api/trash/"+contact+"/restore").principal(admin)).andExpect(status().isConflict());
  age(contact,31);age(customer,31);purge();assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customer_contacts",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_trash_entries",Integer.class));
 }
 @Test void permissionsAreModuleSpecific() throws Exception {
  long entry=trash("CUSTOMER",1);
  mvc.perform(post("/api/trash/"+entry+"/restore").principal(tech)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/trash/CUSTOMER/2").principal(tech)).andExpect(status().isForbidden());
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_READ','DENY' FROM penny_users WHERE email=?",tech.getName());
  mvc.perform(get("/api/trash").principal(tech)).andExpect(jsonPath("$.total").value(0));
  db.update("INSERT INTO penny_user_permissions SELECT id,'TRASH_DELETE','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(delete("/api/trash/CATALOG_PRODUCT/1").principal(admin)).andExpect(status().isForbidden());
  mvc.perform(get("/api/auth/me").principal(admin)).andExpect(jsonPath("$.canDeleteRecords").value(false));
 }
 @Test void otherEntitiesHideAndRestoreWithoutDataLoss() throws Exception {
  db.update("INSERT INTO penny_workstations(id,customer_id,name,active) VALUES (9,2,'Desk',false)");
  db.update("INSERT INTO penny_installed_products(id,customer_id,name,category,origin) VALUES (8,2,'Legacy','HARDWARE','THIRD_PARTY')");
  long station=trash("WORKSTATION",9),installed=trash("INSTALLED_PRODUCT",8),catalog=trash("CATALOG_PRODUCT",1);
  mvc.perform(get("/api/workstations/9").principal(admin)).andExpect(status().isNotFound());mvc.perform(get("/api/installed-products/8").principal(admin)).andExpect(status().isNotFound());mvc.perform(get("/api/catalog-products/1").principal(admin)).andExpect(status().isNotFound());
  mvc.perform(get("/api/overview").principal(admin)).andExpect(jsonPath("$.catalog").value(0)).andExpect(jsonPath("$.installed").value(0));
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_installed_products SET name='Alterato' WHERE id=8"));
  for(long entry:new long[]{station,installed,catalog})mvc.perform(post("/api/trash/"+entry+"/restore").principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/workstations/9").principal(admin)).andExpect(jsonPath("$.active").value(false));
  mvc.perform(get("/api/installed-products/8").principal(admin)).andExpect(jsonPath("$.name").value("Legacy"));
 }
 @Test void unexpectedDependenciesBlockOnlyThatPurgeAndAreReported() throws Exception {
  long catalog=trash("CATALOG_PRODUCT",1),customer=trash("CUSTOMER",1);age(catalog,31);age(customer,31);
  db.execute("CREATE TABLE trash_test_dependency(product_id bigint REFERENCES penny_catalog_products(id))");
  try{
   db.update("INSERT INTO trash_test_dependency VALUES (1)");purge();
   assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_catalog_products WHERE id=1",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=1",Integer.class));
   assertNotNull(db.queryForObject("SELECT blocked_reason FROM penny_trash_entries WHERE id=?",String.class,catalog));
  }finally{db.execute("DROP TABLE trash_test_dependency");}
 }
 @Test void concurrentLinkWaitsAndRejectsTrashedParent() throws Exception {
  try(var connection=db.getDataSource().getConnection()){
   connection.setAutoCommit(false);try(var statement=connection.createStatement()){statement.executeUpdate("UPDATE penny_customers SET deleted_at=now() WHERE id=1");}
   var executor=Executors.newSingleThreadExecutor();var started=new CountDownLatch(1);
   try{
    var result=executor.submit(()->{started.countDown();try{db.update("INSERT INTO penny_workstations(customer_id,name) VALUES (1,'Race')");return "OK";}catch(DataIntegrityViolationException e){return "BLOCKED";}});
    assertTrue(started.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->result.get(200,TimeUnit.MILLISECONDS));connection.commit();assertEquals("BLOCKED",result.get(5,TimeUnit.SECONDS));
   }finally{connection.rollback();executor.shutdownNow();}
  }
 }
 @Test void contactMoveSerializesWithParentTrash() throws Exception {
  db.update("INSERT INTO penny_customer_contacts(id,customer_id,full_name) VALUES (7,1,'Ada')");
  try(var connection=db.getDataSource().getConnection()){
   connection.setAutoCommit(false);try(var statement=connection.createStatement()){statement.executeUpdate("UPDATE penny_customers SET deleted_at=now() WHERE id=1");}
   var executor=Executors.newSingleThreadExecutor();var start=new CountDownLatch(1);
   try{
    var result=executor.submit(()->{start.countDown();return mvc.perform(delete("/api/trash/CONTACT/7").principal(admin)).andReturn().getResponse().getStatus();});
    assertTrue(start.await(5,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->result.get(200,TimeUnit.MILLISECONDS));connection.commit();assertEquals(409,result.get(5,TimeUnit.SECONDS));
   }finally{connection.rollback();executor.shutdownNow();}
  }
 }
 @Test void manualEmptyDoesNotWaitThirtyDaysAndRequiresConfirmation() throws Exception {
  trash("CUSTOMER",1);trash("CATALOG_PRODUCT",1);
  mvc.perform(post("/api/trash/empty").principal(admin).contentType(APPLICATION_JSON).content("{\"confirm\":false}")).andExpect(status().isBadRequest());
  assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_trash_entries",Integer.class));
  mvc.perform(get("/api/trash").principal(admin).param("q","Aurora")).andExpect(jsonPath("$.purgeableTotal").value(2)).andExpect(jsonPath("$.canEmpty").value(true));
  mvc.perform(post("/api/trash/empty").principal(admin).contentType(APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.purged").value(2)).andExpect(jsonPath("$.blocked").value(0));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_trash_entries",Integer.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=1",Integer.class));
 }
 @Test void manualEmptyRespectsModulePermissionsAndKeepsBlockedRows() throws Exception {
  trash("CUSTOMER",1);trash("CATALOG_PRODUCT",1);
  mvc.perform(post("/api/trash/empty").principal(tech).contentType(APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isForbidden());
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_WRITE','DENY' FROM penny_users WHERE email=?",admin.getName());
  db.execute("CREATE TABLE trash_test_dependency(product_id bigint REFERENCES penny_catalog_products(id))");
  try{db.update("INSERT INTO trash_test_dependency VALUES (1)");
   mvc.perform(post("/api/trash/empty").principal(admin).contentType(APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.purged").value(0)).andExpect(jsonPath("$.blocked").value(1));
   assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_trash_entries",Integer.class));
  }finally{db.execute("DROP TABLE trash_test_dependency");}
 }
 @Test void manualEmptySkipsLockedEntriesAndKeepsAuditActor() throws Exception {
  long entry=trash("CUSTOMER",1);trash("CATALOG_PRODUCT",1);
  mvc.perform(post("/api/trash/empty").principal(admin)).andExpect(status().isBadRequest());
  try(var connection=db.getDataSource().getConnection()){
   connection.setAutoCommit(false);
   try(var statement=connection.prepareStatement("SELECT id FROM penny_trash_entries WHERE id=? FOR UPDATE")){statement.setLong(1,entry);statement.executeQuery();}
   mvc.perform(post("/api/trash/empty").principal(admin).contentType(APPLICATION_JSON).content("{\"confirm\":true}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.purged").value(1)).andExpect(jsonPath("$.skipped").value(1));
   connection.rollback();
  }
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_trash_entries",Integer.class));
  assertEquals(admin.getName(),db.queryForObject("SELECT actor FROM penny_m2_audit WHERE action='TRASH_PURGED'",String.class));
 }
 @Test void migrationPreservesAllLegacyFields(){
  String schema="trash_legacy_"+UUID.randomUUID().toString().replace("-","");var source=new DriverManagerDataSource(URL+"?currentSchema="+schema,USER,PASSWORD);
  try{
   Flyway.configure().dataSource(source).schemas(schema).target("105").load().migrate();var legacy=new JdbcTemplate(source);legacy.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Conservare')");var before=legacy.queryForMap("SELECT * FROM penny_customers WHERE id=1");
   Flyway.configure().dataSource(source).schemas(schema).load().migrate();var after=legacy.queryForMap("SELECT * FROM penny_customers WHERE id=1");assertNull(after.remove("deleted_at"));assertEquals("PROSPECT",after.remove("lifecycle_status"));after.keySet().retainAll(before.keySet());assertEquals(before,after);assertEquals(0,legacy.queryForObject("SELECT count(*) FROM penny_trash_entries",Integer.class));
  }finally{db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
 }
 @Test void notesMigrationPreservesTrashedCatalog(){
  String schema="notes_legacy_"+UUID.randomUUID().toString().replace("-","");var source=new DriverManagerDataSource(URL+"?currentSchema="+schema,USER,PASSWORD);
  try{Flyway.configure().dataSource(source).schemas(schema).target("114").load().migrate();var legacy=new JdbcTemplate(source);legacy.update("INSERT INTO penny_catalog_products(code,name,category,origin) VALUES ('OLD','Conservare','SOFTWARE','THIRD_PARTY')");legacy.update("UPDATE penny_catalog_products SET deleted_at=now()");var before=legacy.queryForMap("SELECT * FROM penny_catalog_products");Flyway.configure().dataSource(source).schemas(schema).load().migrate();var after=legacy.queryForMap("SELECT * FROM penny_catalog_products");after.keySet().retainAll(before.keySet());assertEquals(before,after);
  }finally{db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
 }
}
