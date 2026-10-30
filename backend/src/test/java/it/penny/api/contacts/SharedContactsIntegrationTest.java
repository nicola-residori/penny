package it.penny.api.contacts;

import it.penny.api.PennyApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(classes=PennyApplication.class,properties="penny.trash.cleanup-enabled=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class SharedContactsIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=m109_contacts");
  p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));
  p.add("spring.flyway.schemas",()->"m109_contacts");p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
 }
 @Autowired JdbcTemplate db; @Autowired WebApplicationContext context;
 MockMvc mvc; final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("contacts@test.invalid","unused");
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).build();db.execute("TRUNCATE penny_customers,penny_users,penny_m2_audit CASCADE");
  db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'Contacts','unused',false)",admin.getName());
  db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code='ADMIN'",admin.getName());
  db.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Uno'),(2,'COMPANY','Due')");
 }
 @Test void legacyInsertBackfillsAndLastUnlinkIsRejected(){
  long id=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Ada') RETURNING id",Long.class);
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_contact_customers WHERE contact_id=?",Integer.class,id));
  assertThrows(DataIntegrityViolationException.class,()->db.update("DELETE FROM penny_contact_customers WHERE contact_id=?",id));
 }
 @Test void sharedContactSurvivesCustomerPurgeAndSiteOwnershipIsEnforced(){
  long id=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Ada') RETURNING id",Long.class);
  db.update("INSERT INTO penny_contact_customers(contact_id,customer_id) VALUES (?,2)",id);
  assertThrows(DataIntegrityViolationException.class,()->db.update("DELETE FROM penny_customers WHERE id=1"));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customer_contacts WHERE id=?",Integer.class,id));
  long site=db.queryForObject("INSERT INTO penny_customer_sites(customer_id,name) VALUES (1,'HQ') RETURNING id",Long.class);
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_contact_customers SET site_id=? WHERE contact_id=? AND customer_id=2",site,id));
  db.update("UPDATE penny_contact_customers SET site_id=? WHERE contact_id=? AND customer_id=1",site,id);
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_customer_sites SET customer_id=2 WHERE id=?",site));
 }
 @Test void apiRequiresLinksAndSupportsSharedSearchAndUnlink() throws Exception {
  mvc.perform(post("/api/contacts").principal(admin).contentType(APPLICATION_JSON).content("{\"fullName\":\"Ada\",\"links\":[]}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/contacts").principal(admin).contentType(APPLICATION_JSON).content("{\"fullName\":\"Ada\",\"links\":[{\"customerId\":1},{\"customerId\":2}]}" )).andExpect(status().isOk());
  long id=db.queryForObject("SELECT id FROM penny_customer_contacts",Long.class);
  mvc.perform(get("/api/contacts").param("q","Ada").principal(admin)).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].customerCount").value(2));
  mvc.perform(delete("/api/contacts/"+id+"/customers/1").principal(admin)).andExpect(status().isOk());
  mvc.perform(delete("/api/contacts/"+id+"/customers/2").principal(admin)).andExpect(status().isConflict());
 }
 @Test void sharedContactCanBeTrashedRestoredAndEditedWithTrashedLegacyAnchor() throws Exception {
  long id=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Ada') RETURNING id",Long.class);
  db.update("INSERT INTO penny_contact_customers(contact_id,customer_id) VALUES (?,2)",id);
  db.update("UPDATE penny_customers SET deleted_at=now() WHERE id=1");
  mvc.perform(delete("/api/contacts/"+id+"/customers/2").principal(admin)).andExpect(status().isConflict());
  mvc.perform(delete("/api/trash/CONTACT/"+id).principal(admin)).andExpect(status().isOk());
  long entry=db.queryForObject("SELECT id FROM penny_trash_entries WHERE entity_type='CONTACT' AND entity_id=?",Long.class,id);
  mvc.perform(post("/api/trash/"+entry+"/restore").principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/contacts/"+id).principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.customerCount").value(1));
  mvc.perform(put("/api/contacts/"+id).principal(admin).contentType(APPLICATION_JSON).content("{\"fullName\":\"Ada aggiornata\",\"links\":[{\"customerId\":2}]}" )).andExpect(status().isOk());
  assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_contact_customers WHERE contact_id=?",Integer.class,id));
  mvc.perform(delete("/api/contacts/"+id+"/customers/1").principal(admin)).andExpect(status().isOk());
  db.update("DELETE FROM penny_customers WHERE id=1");
  assertEquals("Ada aggiornata",db.queryForObject("SELECT full_name FROM penny_customer_contacts WHERE id=?",String.class,id));
 }
 @Test void directWritesRejectDeletedParentsAndContactsAndUnrelatedAnchor(){
  long id=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Ada') RETURNING id",Long.class);
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_customer_contacts SET customer_id=2 WHERE id=?",id));
  db.update("UPDATE penny_customers SET deleted_at=now() WHERE id=2");
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_contact_customers(contact_id,customer_id) VALUES (?,2)",id));
  db.update("UPDATE penny_customers SET deleted_at=NULL WHERE id=2");
  db.update("UPDATE penny_customer_contacts SET deleted_at=now() WHERE id=?",id);
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_contact_customers(contact_id,customer_id) VALUES (?,2)",id));
 }
 @Test void exclusiveCustomerWithSiteAndContactCanBePurged(){
  long id=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Ada') RETURNING id",Long.class);
  long site=db.queryForObject("INSERT INTO penny_customer_sites(customer_id,name) VALUES (1,'Sede') RETURNING id",Long.class);
  db.update("UPDATE penny_contact_customers SET site_id=? WHERE contact_id=?",site,id);
  db.update("DELETE FROM penny_customers WHERE id=1");
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customer_contacts WHERE id=?",Integer.class,id));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customer_sites WHERE id=?",Integer.class,site));
 }
 @Autowired javax.sql.DataSource source;
 @Test void concurrentUnlinksCannotCommitAnUnassociatedContact() throws Exception {
  long id=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Ada') RETURNING id",Long.class);
  db.update("INSERT INTO penny_contact_customers(contact_id,customer_id) VALUES (?,2)",id);
  var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try(var first=source.getConnection()){
   first.setAutoCommit(false);
   try(var update=first.prepareStatement("UPDATE penny_customer_contacts SET customer_id=2 WHERE id=?")){update.setLong(1,id);update.executeUpdate();}
   try(var remove=first.prepareStatement("DELETE FROM penny_contact_customers WHERE contact_id=? AND customer_id=1")){remove.setLong(1,id);remove.executeUpdate();}
   var started=new java.util.concurrent.CountDownLatch(1);
   var second=executor.submit(()->{
    try(var connection=source.getConnection()){
     connection.setAutoCommit(false);
     try{
      try(var timeout=connection.createStatement()){timeout.execute("SET LOCAL lock_timeout='5s'");}
      started.countDown();
      try(var remove=connection.prepareStatement("DELETE FROM penny_contact_customers WHERE contact_id=? AND customer_id=2")){remove.setLong(1,id);remove.executeUpdate();}
      connection.commit();return "COMMITTED";
     }catch(java.sql.SQLException failure){connection.rollback();return failure.getSQLState();}
    }
   });
   assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));first.commit();
   assertEquals("23514",second.get(10,java.util.concurrent.TimeUnit.SECONDS));
   assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_contact_customers WHERE contact_id=?",Integer.class,id));
  }finally{executor.shutdownNow();}
 }
 @Test void contactLinksExposeSiteTypesAndEmptyListWithoutSite() throws Exception {
  long id=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Ada') RETURNING id",Long.class);
  long site=db.queryForObject("INSERT INTO penny_customer_sites(customer_id,name,site_types) VALUES (1,'HQ','[\"LEGAL\",\"COMMERCIAL\"]'::jsonb) RETURNING id",Long.class);
  db.update("UPDATE penny_contact_customers SET site_id=?,role='Direzione' WHERE contact_id=? AND customer_id=1",site,id);
  db.update("INSERT INTO penny_contact_customers(contact_id,customer_id,role) VALUES (?,2,'Consulente')",id);
  mvc.perform(get("/api/contacts/"+id).principal(admin)).andExpect(status().isOk())
   .andExpect(jsonPath("$.links[0].customerId").value(2)).andExpect(jsonPath("$.links[0].siteTypes").isEmpty())
   .andExpect(jsonPath("$.links[1].siteTypes[0]").value("LEGAL")).andExpect(jsonPath("$.links[1].siteTypes[1]").value("COMMERCIAL"))
   .andExpect(jsonPath("$.links[1].role").value("Direzione"));
 }


 @Test void structuredContactFieldsRoundTripAndLegacyUpdatesPreserveDetails() throws Exception {
  mvc.perform(post("/api/contacts").principal(admin).contentType(APPLICATION_JSON).content("""
   {"fullName":"Ada Lovelace","firstName":"Ada","lastName":"Lovelace","phone":"0123","companyMobile":"333","personalMobile":"444","email":"ada@example.com","contactStatus":"DISMISSED","contactReference":"rif. tecnico","links":[{"customerId":1,"role":"Tecnico"}]}
   """)).andExpect(status().isOk());
  long id=db.queryForObject("SELECT id FROM penny_customer_contacts",Long.class);
  mvc.perform(get("/api/contacts/"+id).principal(admin)).andExpect(jsonPath("$.firstName").value("Ada")).andExpect(jsonPath("$.personalMobile").value("444")).andExpect(jsonPath("$.contactStatus").value("DISMISSED"));
  mvc.perform(put("/api/contacts/"+id).principal(admin).contentType(APPLICATION_JSON).content("{\"fullName\":\"Ada aggiornata\",\"links\":[{\"customerId\":1}]}" )).andExpect(status().isOk());
  mvc.perform(get("/api/customers/1/contacts").principal(admin)).andExpect(jsonPath("$[0].companyMobile").value("333")).andExpect(jsonPath("$[0].lastName").value("Lovelace"));
 }
 @Test void customerCommercialStatusCanBeChangedManually() throws Exception {
  String body="{\"kind\":\"COMPANY\",\"name\":\"Uno\",\"country\":\"IT\",\"lifecycleStatus\":\"CUSTOMER\"}";
  mvc.perform(put("/api/customers/1").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
  assertEquals("CUSTOMER",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=1",String.class));
  mvc.perform(put("/api/customers/1").principal(admin).contentType(APPLICATION_JSON).content(body.replace("CUSTOMER","PROSPECT"))).andExpect(status().isOk());
  assertEquals("PROSPECT",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=1",String.class));
 }
}
