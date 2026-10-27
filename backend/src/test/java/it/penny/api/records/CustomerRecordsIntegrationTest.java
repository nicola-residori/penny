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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
@SpringBootTest(classes=PennyApplication.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class CustomerRecordsIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=m106_api");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"m106_api");p.add("spring.jpa.hibernate.ddl-auto",()->"validate");}
 @Autowired JdbcTemplate db;@Autowired WebApplicationContext context;MockMvc mvc;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("records@test.invalid","unused");
 long customer;
 long contractType(){return db.queryForObject("SELECT t.id FROM penny_product_types t JOIN penny_product_categories c ON c.id=t.category_id WHERE c.operational_kind='CONTRACT' AND NOT t.obsolete LIMIT 1",Long.class);}
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).build();db.execute("TRUNCATE penny_customer_records,penny_customers,penny_users CASCADE");db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES ('records@test.invalid','Records','unused',false)");db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email='records@test.invalid' AND r.code='ADMIN'");customer=db.queryForObject("INSERT INTO penny_customers(kind,name) VALUES ('COMPANY','Records test') RETURNING id",Long.class);}
 long create(String body) throws Exception{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(post("/api/customer-records").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();}
 @Test void trainingWithoutSaleRetainsManualParticipantAndTrashRoundTrip() throws Exception {
  long id=create("{\"customerId\":"+customer+",\"kind\":\"TRAINING\",\"name\":\"CAD\",\"details\":{\"venue\":\"ONLINE\",\"price\":\"123.45\",\"participants\":[{\"name\":\"Anna\"}]}}");
  mvc.perform(get("/api/customer-records/"+id).principal(admin)).andExpect(jsonPath("$.details.participants[0].name").value("Anna")).andExpect(jsonPath("$.details.price").value("123.45"));
  mvc.perform(delete("/api/trash/CUSTOMER/"+customer).principal(admin)).andExpect(status().isConflict());
  mvc.perform(delete("/api/trash/CUSTOMER_RECORD/"+id).principal(admin)).andExpect(status().isOk());
  long trash=db.queryForObject("SELECT id FROM penny_trash_entries WHERE entity_type='CUSTOMER_RECORD' AND entity_id=?",Long.class,id);
  mvc.perform(post("/api/trash/"+trash+"/restore").principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/customer-records/"+id).principal(admin)).andExpect(status().isOk());
 }
 @Test void invalidHoursAndForeignParticipantAreRejected() throws Exception {
  mvc.perform(post("/api/customer-records").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":"+customer+",\"kind\":\"CONTRACT\",\"name\":\"Supporto\",\"details\":{\"contractType\":\"HOURLY\",\"totalHours\":\"10\",\"remainingHours\":\"11\"}}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/customer-records").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":"+customer+",\"kind\":\"TRAINING\",\"name\":\"CAD\",\"details\":{\"participants\":[{\"contactId\":999999999}]}}")).andExpect(status().isBadRequest());
 }
 @Test void contractExpiryAppearsInAuthorizedCalendar() throws Exception {
  long id=create("{\"productTypeId\":"+contractType()+",\"customerId\":"+customer+",\"kind\":\"CONTRACT\",\"name\":\"Assistenza\",\"expiresOn\":\"2026-11-15\",\"details\":{\"contractType\":\"GENERAL\"}}");
  mvc.perform(get("/api/customer-record-deadlines?from=2026-11-01&to=2026-11-30").principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].recordId").value(id));
 }
 @Test void unnamedContractRequiresTypeAndCollaboratorCostDoesNotImplyExternalChannel() throws Exception {
  mvc.perform(post("/api/customer-records").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":"+customer+",\"kind\":\"CONTRACT\",\"details\":{}}")).andExpect(status().isBadRequest());
  long id=create("{\"customerId\":"+customer+",\"kind\":\"CONTRACT\",\"productTypeId\":"+contractType()+",\"details\":{\"collaboratorCost\":\"25\"}}");
  mvc.perform(get("/api/customer-records/"+id).principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.name").isNotEmpty()).andExpect(jsonPath("$.details.collaboratorCost").value("25")).andExpect(jsonPath("$.details.supplyChannel").doesNotExist());
  long external=create("{\"customerId\":"+customer+",\"kind\":\"CONTRACT\",\"productTypeId\":"+contractType()+",\"details\":{\"supplyChannel\":\"EXTERNAL_SUPPLY\"}}");
  mvc.perform(get("/api/customer-records/"+external).principal(admin)).andExpect(jsonPath("$.details.supplyChannel").value("EXTERNAL_SUPPLY"));
 }
}
