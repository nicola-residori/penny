package it.penny.api.customerworkspace;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=PennyApplication.class,properties="penny.trash.cleanup-enabled=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class CustomerLifecycleIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=customer_lifecycle");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"customer_lifecycle");}
 @Autowired JdbcTemplate db;@Autowired WebApplicationContext context;MockMvc mvc;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("lifecycle@test.invalid","unused");
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).build();db.execute("TRUNCATE penny_customers,penny_catalog_products,penny_users,penny_m2_audit,penny_trash_entries RESTART IDENTITY CASCADE");db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'Lifecycle','unused',false)",admin.getName());db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code='ADMIN'",admin.getName());db.update("INSERT INTO penny_customers(id,kind,name,lifecycle_status) VALUES (10,'COMPANY','Cliente esistente','CUSTOMER')");}
 @Test void firstSaleConvertsAutomaticallyOnceAndFailedSalesDoNot() throws Exception {
  mvc.perform(post("/api/customers").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Prospect nuovo\"}")).andExpect(status().isOk());
  long id=db.queryForObject("SELECT id FROM penny_customers WHERE name='Prospect nuovo'",Long.class);
  mvc.perform(put("/api/customers/"+id).principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Prospect modificato\"}")).andExpect(status().isOk());
  db.update("INSERT INTO penny_installed_products(customer_id,category,name,origin) VALUES (?,'HARDWARE','Pregresso','THIRD_PARTY')",id);
  assertEquals("PROSPECT",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=?",String.class,id));
  db.update("INSERT INTO penny_catalog_products(id,code,name,category,origin) VALUES (1,'TEST','Prodotto','HARDWARE','THIRD_PARTY')");
  String body="{\"customerId\":"+id+",\"catalogProductId\":1,\"soldOn\":\"2026-10-25\",\"quantity\":1}";
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content(body.replace("quantity\":1","quantity\":0"))).andExpect(status().isBadRequest());
  assertEquals("PROSPECT",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=?",String.class,id));
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
  mvc.perform(get("/api/customers/"+id).principal(admin)).andExpect(jsonPath("$.lifecycleStatus").value("CUSTOMER"));
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_m2_audit WHERE action='CUSTOMER_STATUS_CHANGED'",Integer.class));
  assertEquals(admin.getName(),db.queryForObject("SELECT actor FROM penny_m2_audit WHERE action='CUSTOMER_STATUS_CHANGED'",String.class));
  long sale=db.queryForObject("SELECT min(id) FROM penny_sales WHERE customer_id=?",Long.class,id);
  mvc.perform(post("/api/sales/"+sale+"/cancel").principal(admin).contentType(APPLICATION_JSON).content("{\"reason\":\"Rettifica\"}")).andExpect(status().isOk());
  assertEquals("CUSTOMER",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=?",String.class,id));
  mvc.perform(get("/api/customers").param("lifecycleStatus","CUSTOMER").principal(admin)).andExpect(jsonPath("$.total").value(2));
 }
 @Test void commercialStatusCanBeSetManuallyButSalesPermissionsAreStillRequired() throws Exception {
  mvc.perform(post("/api/customers").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Manuale\",\"lifecycleStatus\":\"CUSTOMER\"}")).andExpect(status().isOk());
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"lifecycleStatus\":\"PROSPECT\"}")).andExpect(status().isOk());
  assertEquals("CUSTOMER",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE name='Manuale'",String.class));
  assertEquals("PROSPECT",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=10",String.class));
  mvc.perform(get("/api/customers").param("lifecycleStatus","INVALID").principal(admin)).andExpect(status().isBadRequest());
  db.update("INSERT INTO penny_user_permissions SELECT id,'SALES_WRITE','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":10,\"catalogProductId\":1,\"soldOn\":\"2026-10-25\",\"quantity\":1}")).andExpect(status().isForbidden());
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_sales",Integer.class));
  // Changing a customer status grants no ability to create a sale.
  assertEquals("PROSPECT",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=10",String.class));
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_WRITE','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"lifecycleStatus\":\"CUSTOMER\"}")).andExpect(status().isForbidden());
  assertEquals("PROSPECT",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=10",String.class));
 }
 @Test void businessFieldsPreserveOmittedAndRejectInvalidReferences() throws Exception {
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"sector\":\"CAD\",\"isInsolvent\":true,\"isCollaborator\":true}")).andExpect(status().isOk());
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente aggiornato\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/customers/10").principal(admin)).andExpect(jsonPath("$.sector").value("CAD")).andExpect(jsonPath("$.isInsolvent").value(true)).andExpect(jsonPath("$.isCollaborator").value(true)).andExpect(jsonPath("$.createdAt").exists());
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"companyStatus\":\"REPLACED\"}")).andExpect(status().is4xxClientError());
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"commercialContactId\":999999}")).andExpect(status().is4xxClientError());
  mvc.perform(get("/api/customers").principal(admin).param("sort","name; DROP TABLE penny_customers")).andExpect(status().isBadRequest());
 }
 @Test void designatedContactsAndCompanyCyclesAreProtected() throws Exception {
  db.update("INSERT INTO penny_customers(id,kind,name) VALUES (20,'COMPANY','Successore')");
  long contact=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (10,'Referente') RETURNING id",Long.class);
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"commercialContactId\":"+contact+",\"companyStatus\":\"REPLACED\",\"replacementCustomerId\":20}")).andExpect(status().isOk());
  mvc.perform(put("/api/customers/20").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Successore\",\"replacementCustomerId\":10}")).andExpect(status().isConflict());
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("UPDATE penny_customer_contacts SET deleted_at=now() WHERE id=?",contact));
  mvc.perform(put("/api/contacts/"+contact).principal(admin).contentType(APPLICATION_JSON).content("{\"fullName\":\"Referente\",\"links\":[{\"customerId\":20}]}")).andExpect(status().isConflict());
  mvc.perform(put("/api/customers/20").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Successore\",\"commercialContactId\":"+contact+"}")).andExpect(status().isConflict());
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_WRITE','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"isInsolvent\":true}")).andExpect(status().isForbidden());
 }
 @Test void referencedCompanyCannotBeTrashedUntilReferencesAreCleared() throws Exception {
  db.update("INSERT INTO penny_customers(id,kind,name) VALUES (20,'COMPANY','Azienda riferimento')");
  db.update("UPDATE penny_customers SET previous_customer_id=20 WHERE id=10");
  mvc.perform(delete("/api/trash/CUSTOMER/20").principal(admin)).andExpect(status().isConflict());
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Ancora modificabile\"}")).andExpect(status().isOk());
  mvc.perform(patch("/api/customers/10/archive").principal(admin).contentType(APPLICATION_JSON).content("{\"archived\":true}")).andExpect(status().isOk());
  mvc.perform(patch("/api/customers/10/archive").principal(admin).contentType(APPLICATION_JSON).content("{\"archived\":false}")).andExpect(status().isOk());
  db.update("INSERT INTO penny_catalog_products(id,code,name,category,origin) VALUES (1,'REF','Prodotto','HARDWARE','THIRD_PARTY')");
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":10,\"catalogProductId\":1,\"soldOn\":\"2026-10-25\",\"quantity\":1}")).andExpect(status().isOk());
  assertEquals("CUSTOMER",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=10",String.class));
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"previousCustomerId\":null,\"companyStatus\":\"REPLACED\",\"replacementCustomerId\":20}")).andExpect(status().isOk());
  mvc.perform(delete("/api/trash/CUSTOMER/20").principal(admin)).andExpect(status().isConflict());
  mvc.perform(put("/api/customers/10").principal(admin).contentType(APPLICATION_JSON).content("{\"kind\":\"COMPANY\",\"name\":\"Cliente\",\"companyStatus\":\"ACTIVE\",\"replacementCustomerId\":null}")).andExpect(status().isOk());
  mvc.perform(delete("/api/trash/CUSTOMER/20").principal(admin)).andExpect(status().isOk());
  assertNotNull(db.queryForObject("SELECT deleted_at FROM penny_customers WHERE id=20",java.sql.Timestamp.class));
 }
}
