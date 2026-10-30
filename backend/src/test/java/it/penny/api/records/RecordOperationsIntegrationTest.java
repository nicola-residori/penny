package it.penny.api.records;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
@SpringBootTest(classes=PennyApplication.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class RecordOperationsIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=record_operations");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"record_operations");}
 @Autowired JdbcTemplate db;@Autowired WebApplicationContext context;MockMvc mvc;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("records-ops-admin@test.invalid","unused"),none=new UsernamePasswordAuthenticationToken("records-ops-none@test.invalid","unused");
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).build();db.execute("TRUNCATE penny_customers,penny_users,penny_intervention_counters CASCADE");for(String role:new String[]{"ADMIN","NONE"}){String email="records-ops-"+role.toLowerCase()+"@test.invalid";db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,?,'unused',false)",email,role);db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code=?",email,role);}db.update("INSERT INTO penny_customers(id,kind,name,lifecycle_status) VALUES (1,'COMPANY','Alfa','CUSTOMER'),(2,'COMPANY','Beta','CUSTOMER')");db.update("INSERT INTO penny_customer_records(id,customer_id,kind,name,status) VALUES (1,1,'TECHNICAL','Progetto','IN_PROGRESS')");}
 @Test void createsTasksAndRejectsInvalidProgressAndForbiddenWrites()throws Exception{
  String valid="{\"name\":\"Installazione\",\"progress\":25,\"startsOn\":\"2026-10-01\",\"endsOn\":\"2026-10-03\"}";
  mvc.perform(post("/api/customer-records/1/tasks").principal(none).contentType(APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());
  mvc.perform(post("/api/customer-records/1/tasks").principal(admin).contentType(APPLICATION_JSON).content(valid.replace("25","101"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/customer-records/1/tasks").principal(admin).contentType(APPLICATION_JSON).content(valid)).andExpect(status().isOk());
  mvc.perform(get("/api/customer-records/1/tasks").principal(admin)).andExpect(jsonPath("$.items[0].progress").value(25));
 }
 @Test void renewalsPreserveHistoryAndCoverageTracksEffectiveContracts()throws Exception{
  mvc.perform(get("/api/customers/1/support-coverage").principal(admin)).andExpect(jsonPath("$.hasSupport").value(false));
  db.update("INSERT INTO penny_customer_records(id,customer_id,kind,name,status,starts_on,expires_on,details) VALUES (2,1,'CONTRACT','Supporto','ACTIVE','2025-01-01','2026-01-01','{\"contractType\":\"PHONE\"}')");
  mvc.perform(post("/api/customer-records/2/renewals").principal(admin).contentType(APPLICATION_JSON).content("{\"startsOn\":\"2026-01-02\",\"expiresOn\":\"2030-01-01\",\"reason\":\"Rinnovo annuale\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/customer-records/2/renewals").principal(admin)).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].previousEnd").value("2026-01-01"));
  mvc.perform(get("/api/customers/1/support-coverage").principal(admin)).andExpect(jsonPath("$.hasSupport").value(true));
  mvc.perform(post("/api/customer-records/2/renewals").principal(admin).contentType(APPLICATION_JSON).content("{\"startsOn\":\"2026-01-02\",\"expiresOn\":\"2027-01-01\",\"reason\":\"Errore\"}")).andExpect(status().isBadRequest());
 }
 @Test void interventionReferencesAreSequentialAndLinksCannotCrossCustomers()throws Exception{
  db.update("INSERT INTO penny_customer_records(id,customer_id,kind,name,status,details) VALUES (3,1,'INTERVENTION','Uscita','COMPLETED','{\"activityOn\":\"2031-01-01\"}'),(4,2,'INTERVENTION','Altro','COMPLETED','{\"activityOn\":\"2031-01-02\"}')");
  assertEquals("31.RI.001",db.queryForObject("SELECT reference FROM penny_customer_records WHERE id=3",String.class));assertEquals("31.RI.002",db.queryForObject("SELECT reference FROM penny_customer_records WHERE id=4",String.class));
  mvc.perform(post("/api/customer-records/1/interventions").principal(admin).contentType(APPLICATION_JSON).content("{\"interventionId\":4}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/customer-records/1/interventions").principal(admin).contentType(APPLICATION_JSON).content("{\"interventionId\":3}")).andExpect(status().isOk());
  mvc.perform(get("/api/customer-records/1/interventions").principal(admin)).andExpect(jsonPath("$.total").value(1));
 }
 @Test void renewalScheduledForNextYearRetainsCurrentCoverageAndCorrectionsAreRecorded()throws Exception{
  java.time.LocalDate today=java.time.LocalDate.now(),end=today.plusMonths(2),next=end.plusDays(1);
  db.update("INSERT INTO penny_customer_records(id,customer_id,kind,name,status,starts_on,expires_on,details) VALUES (2,1,'CONTRACT','Supporto','ACTIVE',?,?,'{\"contractType\":\"PHONE\"}')",today.minusMonths(6),end);
  mvc.perform(post("/api/customer-records/2/renewals").principal(admin).contentType(APPLICATION_JSON).content("{\"startsOn\":\""+next+"\",\"expiresOn\":\""+next.plusYears(1)+"\",\"reason\":\"Rinnovo anticipato\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/customers/1/support-coverage").principal(admin)).andExpect(jsonPath("$.hasSupport").value(true));
  db.update("UPDATE penny_customer_records SET status='EXPIRED' WHERE id=2");
  mvc.perform(get("/api/customers/1/support-coverage").principal(admin)).andExpect(jsonPath("$.hasSupport").value(false));
  mvc.perform(put("/api/customer-records/2").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"kind\":\"CONTRACT\",\"status\":\"ACTIVE\",\"startsOn\":\""+today+"\",\"expiresOn\":\""+end+"\",\"details\":{\"contractType\":\"PHONE\"}}")).andExpect(status().isOk());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_contract_renewals WHERE event_type='CORRECTION'",Integer.class));
 }
 @Test void configurableChoicesAreAcceptedAndLinkedRecordsCannotMoveCustomer()throws Exception{
  db.update("INSERT INTO penny_choices(group_code,code,label) VALUES ('TECHNICAL_STATUS','WAITING_CUSTOMER','In attesa cliente') ON CONFLICT DO NOTHING");
  mvc.perform(put("/api/customer-records/1").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"kind\":\"TECHNICAL\",\"name\":\"Progetto\",\"status\":\"WAITING_CUSTOMER\"}")).andExpect(status().isOk());
  db.update("INSERT INTO penny_customer_records(id,customer_id,kind,name,status) VALUES (3,1,'INTERVENTION','Intervento','COMPLETED')");
  db.update("INSERT INTO penny_record_interventions VALUES(1,3)");
  mvc.perform(put("/api/customer-records/3").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":2,\"kind\":\"INTERVENTION\",\"name\":\"Intervento\",\"status\":\"COMPLETED\"}")).andExpect(status().isBadRequest());
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("UPDATE penny_customer_records SET customer_id=2 WHERE id=3"));
 }
 @Test void choiceDirectoryPaginatesAndRequiresAdministrationForChanges()throws Exception{
  int total=db.queryForObject("SELECT count(*) FROM penny_choices",Integer.class);
  mvc.perform(get("/api/choices").principal(admin).param("size","1").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(total)).andExpect(jsonPath("$.items.length()").value(1));
  mvc.perform(post("/api/choices").principal(none).contentType(APPLICATION_JSON).content("{\"group\":\"TEST\",\"code\":\"A\",\"label\":\"Uno\"}")).andExpect(status().isForbidden());
 }
 @Test void fieldValidationKeepsDatesAndCountsStrict(){var json=new com.fasterxml.jackson.databind.ObjectMapper();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->CustomerRecordService.validateDetails("TRAINING",json.readTree("{\"courseMonth\":13}")));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->CustomerRecordService.validateDetails("INTERVENTION",json.readTree("{\"startTime\":\"12:00\",\"endTime\":\"10:00\"}")));}
}
