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
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(classes=PennyApplication.class,properties="penny.trash.cleanup-enabled=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class CustomerWorkspaceIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=m108_workspace");
  p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));
  p.add("spring.flyway.schemas",()->"m108_workspace");p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
 }
 @Autowired JdbcTemplate db; @Autowired WebApplicationContext context;
 MockMvc mvc; long site,product;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("workspace@test.invalid","unused");
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).build();
  db.execute("TRUNCATE penny_customers,penny_catalog_products,penny_users CASCADE");
  db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'Workspace','unused',false)",admin.getName());
  db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code='ADMIN'",admin.getName());
  db.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Cliente uno'),(2,'COMPANY','Cliente due'),(3,'COMPANY','Cliente vuoto')");
  site=db.queryForObject("INSERT INTO penny_customer_sites(customer_id,name) VALUES (1,'Sede') RETURNING id",Long.class);
  product=db.queryForObject("INSERT INTO penny_installed_products(customer_id,category,name,origin,license_key,warranty_until,license_expires_on) VALUES (1,'SOFTWARE','Prodotto','THIRD_PARTY','SECRET','2026-10-25','2026-10-31') RETURNING id",Long.class);
 }
 @Test void installedProductCategoryFilterAndValidation() throws Exception {
  db.update("INSERT INTO penny_installed_products(customer_id,category,name,origin) VALUES (1,'HARDWARE','Hardware','THIRD_PARTY')");
  mvc.perform(get("/api/installed-products").param("customerId","1").param("category","SOFTWARE").param("size","1").principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].name").value("Prodotto"));
  mvc.perform(get("/api/installed-products").param("category","HARDWARE").principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].category").value("HARDWARE"));
  mvc.perform(get("/api/installed-products").param("category","INVALID").principal(admin)).andExpect(status().isBadRequest());
 }
 @Test void catalogCategoryFilterMatchesTotals() throws Exception {
  db.update("INSERT INTO penny_catalog_products(code,name,category,origin) VALUES ('SOFT','Software','SOFTWARE','THIRD_PARTY'),('HARD','Hardware','HARDWARE','THIRD_PARTY')");
  mvc.perform(get("/api/catalog-products").param("category","SOFTWARE").principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].code").value("SOFT"));
  mvc.perform(get("/api/catalog-products").param("category","INVALID").principal(admin)).andExpect(status().isBadRequest());
 }
 @Test void sitesAndAssignmentsRespectOwnershipAndDeletedCustomers() throws Exception {
  mvc.perform(patch("/api/customer-sites/assign-product/"+product).principal(admin).contentType(APPLICATION_JSON).content("{\"siteId\":"+site+"}")).andExpect(status().isOk());
  long other=db.queryForObject("INSERT INTO penny_customer_sites(customer_id,name) VALUES (2,'Altra') RETURNING id",Long.class);
  mvc.perform(patch("/api/customer-sites/assign-product/"+product).principal(admin).contentType(APPLICATION_JSON).content("{\"siteId\":"+other+"}")).andExpect(status().isBadRequest());
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_installed_products SET customer_id=2 WHERE id=?",product));
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_customer_sites SET customer_id=2 WHERE id=?",site));
  long station=db.queryForObject("INSERT INTO penny_workstations(customer_id,name,site_id) VALUES (1,'Postazione',?) RETURNING id",Long.class,site);
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_workstations SET customer_id=2 WHERE id=?",station));
  mvc.perform(patch("/api/customer-sites/assign-product/"+product).principal(admin).contentType(APPLICATION_JSON).content("{\"siteId\":null}")).andExpect(status().isOk());
  db.update("UPDATE penny_customers SET deleted_at=now() WHERE id=3");
  mvc.perform(get("/api/customer-sites").param("customerId","3").principal(admin)).andExpect(status().isNotFound());
  mvc.perform(post("/api/customer-sites").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":3,\"name\":\"Nuova\"}")).andExpect(status().isNotFound());
 }
 @Test void calendarIsInclusiveSecretFreeAndHidesDeletedRows() throws Exception {
  mvc.perform(get("/api/deadlines").param("from","2026-10-25").param("to","2026-10-31").principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].type").value("WARRANTY")).andExpect(jsonPath("$.items[0].licenseKey").doesNotExist());
  mvc.perform(get("/api/deadlines").param("from","2026-10-31").param("to","2026-10-25").principal(admin)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/deadlines").param("from","2026-01-01").param("to","2027-01-02").principal(admin)).andExpect(status().isBadRequest());
  db.update("UPDATE penny_installed_products SET deleted_at=now() WHERE id=?",product);
  mvc.perform(get("/api/deadlines").param("from","2026-10-25").param("to","2026-10-31").principal(admin)).andExpect(jsonPath("$.total").value(0));
 }
 @Test void workspaceAndCalendarHonorPermissions() throws Exception {
  mvc.perform(get("/api/customer-workspace/1").principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.summary.software").value(1)).andExpect(jsonPath("$.summary.hardware").value(0)).andExpect(jsonPath("$.productsTotal").value(1)).andExpect(jsonPath("$.products[0].licenseKey").doesNotExist());
  db.update("INSERT INTO penny_user_permissions SELECT id,'PRODUCTS_READ','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(get("/api/customer-workspace/1").principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.products").isEmpty()).andExpect(jsonPath("$.deadlines").isEmpty()).andExpect(jsonPath("$.permissions.productsRead").value(false));
  mvc.perform(get("/api/deadlines").param("from","2026-10-25").param("to","2026-10-31").principal(admin)).andExpect(status().isForbidden());
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_READ','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(get("/api/customer-workspace/1").principal(admin)).andExpect(status().isForbidden());
 }
 @Test void siteTypesAreMultiplePreservedWhenOmittedAndExplicitlyCleared() throws Exception {
  mvc.perform(get("/api/customer-sites").param("customerId","1").principal(admin)).andExpect(jsonPath("$.items[0].siteTypes").isEmpty());
  String body="{\"customerId\":1,\"name\":\"Sede\",\"siteTypes\":[\"LEGAL\",\"OPERATIONAL\"]}";
  mvc.perform(put("/api/customer-sites/"+site).principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
  mvc.perform(put("/api/customer-sites/"+site).principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"name\":\"Rinominata\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/customer-workspace/1").principal(admin)).andExpect(jsonPath("$.sites[0].siteTypes[0]").value("LEGAL")).andExpect(jsonPath("$.sites[0].siteTypes[1]").value("OPERATIONAL"));
  mvc.perform(put("/api/customer-sites/"+site).principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"name\":\"Sede\",\"siteTypes\":[]}")).andExpect(status().isOk());
  mvc.perform(get("/api/customer-sites").param("customerId","1").principal(admin)).andExpect(jsonPath("$.items[0].siteTypes").isEmpty());
  mvc.perform(post("/api/customer-sites").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"name\":\"Nuova\"}")).andExpect(status().isOk());
  assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_customer_sites WHERE customer_id=1 AND site_types='[]'::jsonb",Integer.class));
 }
 @Test void siteTypesRejectInvalidApiAndDatabaseValues() throws Exception {
  for(String types:java.util.List.of("null","{}","\"LEGAL\"","[null]","[1]","[\"UNKNOWN\"]","[\"LEGAL\",\"LEGAL\"]","[\"LEGAL\",\"ADMINISTRATIVE\",\"OPERATIONAL\",\"COMMERCIAL\",\"WAREHOUSE\",\"LEGAL\"]")) {
   String body="{\"customerId\":1,\"name\":\"Invalid\",\"siteTypes\":"+types+"}";
   mvc.perform(post("/api/customer-sites").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
   mvc.perform(put("/api/customer-sites/"+site).principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
   assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_customer_sites SET site_types=?::jsonb WHERE id=?",types,site));
  }
 }
 @Test void organizationUsesRealSharedLinksAndFiltersTrashWithCustomerReadOnly() throws Exception {
  long shared=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name,email,phone) VALUES (1,'Ada','ada@test.invalid','123') RETURNING id",Long.class);
  db.update("UPDATE penny_contact_customers SET site_id=?,role='Direzione',is_primary=true WHERE contact_id=? AND customer_id=1",site,shared);
  db.update("INSERT INTO penny_contact_customers(contact_id,customer_id,role) VALUES (?,2,'Consulente'),(?,3,'Altro')",shared,shared);
  db.update("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Senza sede')");
  long deleted=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name) VALUES (1,'Cestinato') RETURNING id",Long.class);
  db.update("UPDATE penny_customer_contacts SET deleted_at=now() WHERE id=?",deleted);
  db.update("UPDATE penny_customers SET deleted_at=now() WHERE id=3");
  db.update("UPDATE penny_customers SET archived=true WHERE id=1");
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_WRITE','DENY' FROM penny_users WHERE email=?",admin.getName());
  db.update("INSERT INTO penny_user_permissions SELECT id,'PRODUCTS_READ','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(get("/api/customer-organization/1").principal(admin)).andExpect(status().isOk())
   .andExpect(jsonPath("$.customer.name").value("Cliente uno")).andExpect(jsonPath("$.customer.kind").value("COMPANY")).andExpect(jsonPath("$.customer.lifecycleStatus").value("PROSPECT")).andExpect(jsonPath("$.customer.archived").value(true))
   .andExpect(jsonPath("$.sites[0].siteTypes").isEmpty()).andExpect(jsonPath("$.contacts.length()").value(2))
   .andExpect(jsonPath("$.contacts[0].fullName").value("Ada")).andExpect(jsonPath("$.contacts[0].siteId").value(site))
   .andExpect(jsonPath("$.contacts[0].role").value("Direzione")).andExpect(jsonPath("$.contacts[0].isPrimary").value(true))
   .andExpect(jsonPath("$.contacts[0].customerCount").value(2)).andExpect(jsonPath("$.contacts[1].siteId").isEmpty())
   .andExpect(jsonPath("$.products").doesNotExist()).andExpect(jsonPath("$.licenseKey").doesNotExist());
  mvc.perform(get("/api/customer-organization/2").principal(admin)).andExpect(jsonPath("$.contacts[0].role").value("Consulente")).andExpect(jsonPath("$.contacts[0].siteId").isEmpty());
  mvc.perform(get("/api/customer-organization/3").principal(admin)).andExpect(status().isNotFound());
  mvc.perform(get("/api/customer-organization/99999").principal(admin)).andExpect(status().isNotFound());
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_READ','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(get("/api/customer-organization/1").principal(admin)).andExpect(status().isForbidden());
 }

 @Test void operationalSiteProvinceIsListedAndOmittedUpdatesPreserveIt() throws Exception {
  mvc.perform(put("/api/customer-sites/"+site).principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"name\":\"Sede operativa\",\"street\":\"Via Roma 10\",\"city\":\"Verona\",\"province\":\"VR\",\"siteTypes\":[\"OPERATIONAL\"]}")).andExpect(status().isOk());
  mvc.perform(get("/api/customers/1").principal(admin)).andExpect(jsonPath("$.operationalProvince").value("VR")).andExpect(jsonPath("$.operationalAddress").value("Via Roma 10, Verona"));
  mvc.perform(put("/api/customer-sites/"+site).principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"name\":\"Sede aggiornata\",\"city\":\"Verona\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/customer-sites?customerId=1").principal(admin)).andExpect(jsonPath("$.items[0].province").value("VR"));
  mvc.perform(get("/api/customers/3").principal(admin)).andExpect(jsonPath("$.operationalProvince").isEmpty());
 }
}
