package it.penny.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.penny.api.PennyApplication;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the README contracts with the real filter chain and real database. */
@SpringBootTest(classes=PennyApplication.class)
@EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class DemoDataIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  String url=System.getenv("PENNY_TEST_JDBC_URL");
  p.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema=demo_data");
  p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));
  p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));
  p.add("spring.flyway.schemas",()->"demo_data");
  p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
 }
 @Autowired WebApplicationContext context;
 @Autowired FilterChainProxy security;
 @Autowired JdbcTemplate db;
 @Autowired PasswordEncoder encoder;
 @Autowired ObjectMapper json;
 MockMvc mvc;
 String email;
 static final String PASSWORD="Readme-original-password-2026";
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
  db.execute("TRUNCATE penny_users CASCADE");
  db.execute("TRUNCATE penny_documents,penny_notifications,penny_record_tasks,penny_record_interventions,penny_contract_renewals,penny_margin_events,penny_margin_snapshots,penny_margin_rule_audit,penny_margin_rules,penny_margin_profiles,penny_sales_orders,penny_record_participants,penny_customer_records,penny_sale_allocations,penny_sales,penny_installed_products,penny_workstations,penny_contact_customers,penny_customer_contacts,penny_customer_sites,penny_catalog_products,penny_customers,penny_trash_entries,penny_demo_environment,penny_m2_audit RESTART IDENTITY");
  email="readme-"+UUID.randomUUID()+"@test.invalid";
  db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'README',?,false)",email,encoder.encode(PASSWORD));
  db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code='ADMIN'",email);
 }
 record Csrf(Cookie cookie,String token){}
 Csrf csrf() throws Exception {
  var response=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
  return new Csrf(response.getCookie("XSRF-TOKEN"),json.readTree(response.getContentAsString()).get("token").asText());
 }
 MockHttpServletRequestBuilder secured(MockHttpServletRequestBuilder request,Csrf csrf){return request.cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token());}
 MockHttpSession login(String account,String password) throws Exception {
  Csrf csrf=csrf();
  var result=mvc.perform(secured(post("/api/auth/login").servletPath("/api/auth/login"),csrf).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",account,"password",password))))
    .andExpect(status().isOk()).andReturn();
  return (MockHttpSession)result.getRequest().getSession(false);
 }
 @Test void demoRequiresConfirmationAndIsIdempotent() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
  db.update("INSERT INTO penny_customers(kind,name) VALUES ('COMPANY','Real company')");
  for(int i=0;i<2;i++)mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isOk());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customers WHERE name='Real company'",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO]%'",Integer.class)>=12);
  assertEquals(4,db.queryForObject("SELECT count(DISTINCT category) FROM penny_catalog_products WHERE code LIKE 'DEMO-%'",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_sale_allocations",Integer.class)>0);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_contact_customers GROUP BY contact_id HAVING count(*)>1 LIMIT 1",Integer.class)>1);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO]%' AND kind='PERSON'",Integer.class)>=2);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE name LIKE '[DEMO]%' AND catalog_product_id IS NULL",Integer.class)>=3);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_sales s JOIN penny_customers c ON c.id=s.customer_id WHERE c.name IN ('[DEMO] Alfa Retail','[DEMO] Giulia Riva','[DEMO] Paolo Ferrari')",Integer.class));
  var atlasTypes=db.queryForList("SELECT s.site_types::text FROM penny_customer_sites s JOIN penny_customers c ON c.id=s.customer_id WHERE c.name='[DEMO] E2 Gruppo Atlas' ORDER BY s.name",String.class);
  assertEquals(5,atlasTypes.size());
  String[] expectedTypes={"[\"LEGAL\",\"ADMINISTRATIVE\"]","[\"OPERATIONAL\"]","[\"OPERATIONAL\",\"WAREHOUSE\"]","[\"COMMERCIAL\"]","[\"WAREHOUSE\"]"};
  for(int index=0;index<5;index++)assertEquals(json.readTree(expectedTypes[index]),json.readTree(atlasTypes.get(index)));
  int salesBefore=db.queryForObject("SELECT count(*) FROM penny_sales",Integer.class);
  assertTrue(db.queryForObject("SELECT max(sold_on)-min(sold_on) FROM penny_sales WHERE reference LIKE 'E3-%'",Integer.class)>=729);
  int before=db.queryForObject("SELECT count(*) FROM penny_customers",Integer.class);
  mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isOk());
  assertEquals(before,db.queryForObject("SELECT count(*) FROM penny_customers",Integer.class));
  assertEquals(salesBefore,db.queryForObject("SELECT count(*) FROM penny_sales",Integer.class));
 }
 @Test void resetRequiresExactPhrasePreservesSecurityAndAudits() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  db.update("INSERT INTO penny_customers(kind,name) VALUES ('COMPANY','Real company')");
  int users=db.queryForObject("SELECT count(*) FROM penny_users",Integer.class);
  int roles=db.queryForObject("SELECT count(*) FROM penny_roles",Integer.class);
  int choices=db.queryForObject("SELECT count(*) FROM penny_choices",Integer.class);
  int types=db.queryForObject("SELECT count(*) FROM penny_product_types",Integer.class);
  mvc.perform(secured(post("/api/admin/data/reset"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"reset\"}")).andExpect(status().isBadRequest());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customers",Integer.class));
  mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isOk());
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_installed_products",Integer.class)>0);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_sales WHERE order_id IS NULL",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_sales_orders",Integer.class)>0);
  long maxBefore=db.queryForObject("SELECT max(id) FROM penny_customers",Long.class);
  mvc.perform(secured(post("/api/admin/data/reset"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirmation\":\"RESET\"}")).andExpect(status().isOk());
  for(String table:new String[]{"penny_customer_records","penny_record_participants","penny_customers","penny_customer_contacts","penny_contact_customers","penny_customer_sites","penny_catalog_products","penny_sales","penny_sale_allocations","penny_installed_products","penny_workstations","penny_trash_entries","penny_demo_environment","penny_sales_orders","penny_documents","penny_notifications","penny_record_tasks","penny_record_interventions","penny_contract_renewals","penny_margin_profiles","penny_margin_rules","penny_margin_snapshots","penny_margin_events","penny_margin_rule_audit"})assertEquals(0,db.queryForObject("SELECT count(*) FROM "+table,Integer.class),table);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customers",Integer.class));
  assertEquals(users,db.queryForObject("SELECT count(*) FROM penny_users",Integer.class));
  assertEquals(roles,db.queryForObject("SELECT count(*) FROM penny_roles",Integer.class));
  assertEquals(choices,db.queryForObject("SELECT count(*) FROM penny_choices",Integer.class));
  assertEquals(types,db.queryForObject("SELECT count(*) FROM penny_product_types",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_m2_audit WHERE action='ENVIRONMENT_RESET' AND actor=?",Integer.class,email)>0);
  long next=db.queryForObject("INSERT INTO penny_customers(kind,name) VALUES ('COMPANY','New identity after reset') RETURNING id",Long.class);assertTrue(next>maxBefore,"Reset must not reuse retained audit entity identities");
 }
 @Test void techWithUsersReadCannotManageData() throws Exception {
  db.update("DELETE FROM penny_user_roles WHERE user_id=(SELECT id FROM penny_users WHERE email=?)",email);
  db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code='TECH'",email);
  db.update("INSERT INTO penny_user_permissions SELECT id,'USERS_READ','ALLOW' FROM penny_users WHERE email=?",email);
  MockHttpSession session=login(email,PASSWORD);
  mvc.perform(get("/api/admin/data/state").session(session)).andExpect(status().isForbidden());
  for(String endpoint:new String[]{"demo","reset"})mvc.perform(secured(post("/api/admin/data/"+endpoint),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true,\"confirmation\":\"RESET\"}")).andExpect(status().isForbidden());
 }
 @Test void failedDemoRollsBackEveryInsertedBusinessRow() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  db.update("INSERT INTO penny_customers(kind,name) VALUES ('COMPANY','Existing real customer')");
  db.execute("ALTER TABLE penny_catalog_products ADD CONSTRAINT test_demo_failure CHECK(code NOT LIKE 'DEMO-%')");
  try {
   assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")));
   assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customers",Integer.class));
   assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customer_contacts",Integer.class));
   assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_demo_environment",Integer.class));
  } finally {db.execute("ALTER TABLE penny_catalog_products DROP CONSTRAINT test_demo_failure");}
 }

 @Test void expandedScenariosUpgradeLegacyMarkerWithoutChangingExistingRows() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  long real=db.queryForObject("INSERT INTO penny_customers(kind,name,notes) VALUES ('COMPANY','Real untouched','keep') RETURNING id",Long.class);
  long existingContact=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name,email,role) VALUES (?,'[DEMO] E2 Contatto 80','real@example.invalid','Existing role') RETURNING id",Long.class,real);
  db.update("INSERT INTO penny_demo_environment(id,actor) VALUES (1,?)",email);
  mvc.perform(get("/api/admin/data/state").session(session)).andExpect(jsonPath("$.scenarioVersion").value(1)).andExpect(jsonPath("$.demoUpgradeAvailable").value(true));
  mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.scenarioVersion").value(3));
  assertEquals("keep",db.queryForObject("SELECT notes FROM penny_customers WHERE id=?",String.class,real));
  assertEquals(50,db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO] E2 %'",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customer_sites WHERE name LIKE '[DEMO] E2 %'",Integer.class)>100);
  assertEquals(80,db.queryForObject("SELECT count(*) FROM penny_customer_contacts WHERE full_name LIKE '[DEMO] E2 %' AND id<>?",Integer.class,existingContact));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customer_contacts WHERE id=? AND email='real@example.invalid' AND deleted_at IS NULL AND role='Existing role'",Integer.class,existingContact));
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_contact_customers WHERE contact_id=? AND customer_id=? AND role='Existing role'",Integer.class,existingContact,real));
  assertEquals(40,db.queryForObject("SELECT count(*) FROM penny_catalog_products WHERE code LIKE 'E2-%'",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE name LIKE '[DEMO] E2 %'",Integer.class)>=160);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_installed_products p JOIN penny_customers c ON c.id=p.customer_id WHERE c.name='[DEMO] E2 Gruppo Atlas'",Integer.class)>100);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_sales WHERE reference LIKE 'E2-%'",Integer.class)>=100);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE category='TRAINING'",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO] E2 %' AND kind='PERSON' AND lifecycle_status='CUSTOMER'",Integer.class)>0);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO] E2 %' AND kind='PERSON' AND lifecycle_status='PROSPECT'",Integer.class)>0);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_installed_products p JOIN penny_workstations w ON w.id=p.workstation_id WHERE p.name LIKE '[DEMO] E2 %' AND p.site_id IS NOT NULL AND p.site_id IS DISTINCT FROM w.site_id",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM (SELECT l.contact_id FROM penny_contact_customers l JOIN penny_customer_contacts c ON c.id=l.contact_id WHERE c.full_name LIKE '[DEMO] E2 %' GROUP BY l.contact_id HAVING count(*) BETWEEN 2 AND 4) shared",Integer.class)>=20);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO] E2 %' AND kind='PERSON'",Integer.class)>=8);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO] E2 %' AND lifecycle_status='PROSPECT' AND deleted_at IS NULL",Integer.class)>0);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_sale_allocations a JOIN penny_sales s ON s.id=a.sale_id JOIN penny_installed_products p ON p.id=a.installed_product_id WHERE s.status='CANCELLED' OR p.status='RETIRED'",Integer.class));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_trash_entries WHERE expires_at<=now()",Integer.class));
  assertTrue(db.queryForObject("SELECT count(DISTINCT entity_type) FROM penny_trash_entries",Integer.class)>=5);
  int count=db.queryForObject("SELECT count(*) FROM penny_customers",Integer.class);
  mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.demoUpgradeAvailable").value(false));
  assertEquals(count,db.queryForObject("SELECT count(*) FROM penny_customers",Integer.class));
 }

 @Test void failedExpandedUpgradeKeepsLegacyMarkerAndRows() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  long real=db.queryForObject("INSERT INTO penny_customers(kind,name) VALUES ('COMPANY','Existing before expansion') RETURNING id",Long.class);
  db.update("INSERT INTO penny_demo_environment(id,actor) VALUES (1,?)",email);
  db.execute("ALTER TABLE penny_catalog_products ADD CONSTRAINT test_expanded_failure CHECK(code NOT LIKE 'E2-%')");
  try {
   assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")));
   assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_customers",Integer.class));
   assertEquals("Existing before expansion",db.queryForObject("SELECT name FROM penny_customers WHERE id=?",String.class,real));
   mvc.perform(get("/api/admin/data/state").session(session)).andExpect(jsonPath("$.scenarioVersion").value(1));
  } finally {db.execute("ALTER TABLE penny_catalog_products DROP CONSTRAINT test_expanded_failure");}
 }

 @Test void historicalUpgradePreservesVersionTwoAndRealRowsAndIsIdempotent() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  long real=db.queryForObject("INSERT INTO penny_customers(kind,name,notes) VALUES ('COMPANY','Real untouched history','keep') RETURNING id",Long.class);
  long product=db.queryForObject("INSERT INTO penny_catalog_products(code,name,category,origin) VALUES ('REAL-HISTORY','Real product','HARDWARE','THIRD_PARTY') RETURNING id",Long.class);
  long sale=db.queryForObject("INSERT INTO penny_sales(customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price,reference) VALUES (?,?,'REAL-HISTORY','Real product','THIRD_PARTY','HARDWARE','2020-01-02',1,42,'REAL-ORDER') RETURNING id",Long.class,real,product);
  db.update("INSERT INTO penny_demo_environment(id,actor,scenario_version) VALUES (1,?,2)",email);
  mvc.perform(get("/api/admin/data/state").session(session)).andExpect(jsonPath("$.demoUpgradeAvailable").value(true));
  for(int i=0;i<2;i++)mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.scenarioVersion").value(3));
  assertEquals(48,db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO] E3 %'",Integer.class));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO] E2 %'",Integer.class));
  assertEquals("keep",db.queryForObject("SELECT notes FROM penny_customers WHERE id=?",String.class,real));
  assertEquals("2020-01-02",db.queryForObject("SELECT sold_on::text FROM penny_sales WHERE id=? AND order_id IS NULL",String.class,sale));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_sales WHERE reference LIKE 'E3-%'",Integer.class)>2000);
  assertTrue(db.queryForObject("SELECT max(sold_on)-min(sold_on) FROM penny_sales WHERE reference LIKE 'E3-%'",Integer.class)>=729);
  assertTrue(db.queryForObject("SELECT count(DISTINCT sold_on) FROM penny_sales WHERE reference LIKE 'E3-%'",Integer.class)>=730);
  assertTrue(db.queryForObject("SELECT count(DISTINCT date_trunc('month',first_sale)) FROM (SELECT min(sold_on) first_sale FROM penny_sales WHERE reference LIKE 'E3-%' AND status='CONFIRMED' GROUP BY customer_id) t",Integer.class)>=24);
  assertEquals(4,db.queryForObject("SELECT count(DISTINCT category) FROM penny_sales WHERE reference LIKE 'E3-%'",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_sales WHERE reference LIKE 'E3-%' AND status='CANCELLED'",Integer.class)>0);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_sales s JOIN penny_sales_orders o ON o.id=s.order_id WHERE s.reference LIKE 'E3-%' AND (s.customer_id<>o.customer_id OR s.sold_on<>o.sold_on OR s.status<>o.status)",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customer_records WHERE name LIKE '[DEMO] E3 %'",Integer.class)>=730);
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_catalog_products WHERE code LIKE 'E3-%' AND margin_management<>'NONE'",Integer.class));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_record_tasks t JOIN penny_customer_records r ON r.id=t.record_id WHERE r.name LIKE '[DEMO] E3 %' AND r.kind NOT IN ('TECHNICAL','DEVELOPMENT')",Integer.class));
  assertEquals(4,db.queryForObject("SELECT count(DISTINCT details->>'contractType') FROM penny_customer_records WHERE name LIKE '[DEMO] E3 %' AND kind='CONTRACT'",Integer.class));
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_customer_records WHERE name LIKE '[DEMO] E3 %' AND kind='CONTRACT' AND status='ACTIVE' AND details->>'contractType' IN ('PHONE','PHONE_UPDATES','HOURLY')",Integer.class)>0);
  assertTrue(db.queryForObject("SELECT count(*) FROM penny_record_interventions l JOIN penny_customer_records p ON p.id=l.record_id JOIN penny_customer_records i ON i.id=l.intervention_id WHERE p.name LIKE '[DEMO] E3 %' AND p.kind='TECHNICAL' AND i.kind='INTERVENTION' AND i.customer_id=p.customer_id AND i.details->>'activityOn'=i.starts_on::text AND i.reference ~ '^[0-9]{2}[.]RI[.][0-9]+$'",Integer.class)>100);

 }
 @Test void failedHistoricalUpgradeKeepsVersionTwoMarker() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  db.update("INSERT INTO penny_demo_environment(id,actor,scenario_version) VALUES (1,?,2)",email);
  db.execute("ALTER TABLE penny_catalog_products ADD CONSTRAINT test_history_failure CHECK(code NOT LIKE 'E3-%')");
  try {
   assertThrows(jakarta.servlet.ServletException.class,()->mvc.perform(secured(post("/api/admin/data/demo"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")));
   assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_customers WHERE name LIKE '[DEMO] E3 %'",Integer.class));
   assertEquals(2,db.queryForObject("SELECT scenario_version FROM penny_demo_environment WHERE id=1",Integer.class));
  } finally {db.execute("ALTER TABLE penny_catalog_products DROP CONSTRAINT test_history_failure");}
 }

}
