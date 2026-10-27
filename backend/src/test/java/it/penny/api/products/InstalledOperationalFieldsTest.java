package it.penny.api.products;
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
class InstalledOperationalFieldsTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=installed_operational");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"installed_operational");}
 @Autowired JdbcTemplate db;@Autowired WebApplicationContext context;MockMvc mvc;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("operational@test.invalid","unused");
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).build();db.execute("TRUNCATE penny_customers,penny_catalog_products,penny_users,penny_m2_audit RESTART IDENTITY CASCADE");db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'Operational','unused',false)",admin.getName());db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code='ADMIN'",admin.getName());db.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Prospect')");}
 String input(String extras){return "{\"customerId\":1,\"category\":\"SOFTWARE\",\"name\":\"Licenze terze\",\"origin\":\"THIRD_PARTY\",\"manufacturer\":\"Microsoft\""+extras+"}";}
 @Test void optionalSupplierQuantityAndUsersArePreservedAndDoNotCreateSale() throws Exception {
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"supplier\":\"Distribuzione Alfa\",\"quantity\":10,\"activatedUsers\":7"))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.manufacturer").value("Microsoft")).andExpect(jsonPath("$.supplier").value("Distribuzione Alfa")).andExpect(jsonPath("$.quantity").value(10)).andExpect(jsonPath("$.activatedUsers").value(7));
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(input(""))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.supplier").value("Distribuzione Alfa")).andExpect(jsonPath("$.quantity").value(10));
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(input(",\"supplier\":null,\"quantity\":null,\"activatedUsers\":null"))).andExpect(status().isOk());
  assertNull(db.queryForMap("SELECT supplier,quantity,activated_users FROM penny_installed_products WHERE id=1").get("quantity"));
  assertEquals("PROSPECT",db.queryForObject("SELECT lifecycle_status FROM penny_customers WHERE id=1",String.class));assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_sales",Integer.class));
 }
 @Test void fractionsInvalidCountsAndTrainingInstallationsAreRejected() throws Exception {
  for(String extra:new String[]{",\"quantity\":0",",\"quantity\":1.5",",\"quantity\":1000001",",\"activatedUsers\":-1",",\"activatedUsers\":1.5",",\"supplier\":42"})mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(extra))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input("").replace("SOFTWARE","TRAINING"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"quantity\":1,\"activatedUsers\":0"))).andExpect(status().isOk());
 }
}
