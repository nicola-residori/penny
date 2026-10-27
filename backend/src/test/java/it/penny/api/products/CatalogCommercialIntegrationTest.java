package it.penny.api.products;

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
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(classes=PennyApplication.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class CatalogCommercialIntegrationTest {
 static final String URL=System.getenv("PENNY_TEST_JDBC_URL");
 static final String USER=System.getenv("PENNY_TEST_DB_USER");
 static final String PASSWORD=System.getenv("PENNY_TEST_DB_PASSWORD");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->URL+"?currentSchema=commercial_api");
  p.add("spring.datasource.username",()->USER);p.add("spring.datasource.password",()->PASSWORD);
  p.add("spring.flyway.schemas",()->"commercial_api");p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
 }
 @Autowired JdbcTemplate db;
 @Autowired WebApplicationContext context;
 MockMvc mvc;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("catalog-admin@test.invalid","unused");
 final UsernamePasswordAuthenticationToken tech=new UsernamePasswordAuthenticationToken("catalog-tech@test.invalid","unused");
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).build();
  db.execute("TRUNCATE penny_installed_products,penny_catalog_products,penny_customers,penny_users CASCADE");
  for(String role:new String[]{"ADMIN","TECH"}){
   String email="catalog-"+role.toLowerCase()+"@test.invalid";
   db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,?,'unused',false)",email,role);
   db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code=?",email,role);
  }
  db.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Cliente prova')");
 }
 String catalog(String code,String origin){return "{\"code\":\""+code+"\",\"name\":\"Prodotto\",\"origin\":\""+origin+"\",\"category\":\"SOFTWARE\",\"active\":true}";}
 long entry(String code,String origin){return db.queryForObject("INSERT INTO penny_catalog_products(code,name,origin,category) VALUES (?,'Prodotto',?,'SOFTWARE') RETURNING id",Long.class,code,origin);}
 String installed(String origin,Long id){return "{\"customerId\":1,\"name\":\"Snapshot personalizzato\",\"category\":\"SOFTWARE\",\"origin\":\""+origin+"\",\"catalogProductId\":"+id+"}";}
 @Test void commercialFieldsPreserveOmittedAndClearExplicitNull() throws Exception {
  String body=catalog("COMM","INTERNAL").replace("\"active\":true","\"active\":true,\"manufacturer\":\"Dell\",\"supplier\":\"Distributor\",\"price\":\"125.50\"");
  mvc.perform(post("/api/catalog-products").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk());
  long id=db.queryForObject("SELECT id FROM penny_catalog_products WHERE code='COMM'",Long.class);
  mvc.perform(get("/api/catalog-products").principal(tech).param("q","Distributor")).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].price").value(125.50)).andExpect(jsonPath("$.items[0].manufacturer").value("Dell"));
  mvc.perform(put("/api/catalog-products/"+id).principal(admin).contentType(APPLICATION_JSON).content(catalog("COMM","INTERNAL"))).andExpect(status().isOk());
  assertEquals(new java.math.BigDecimal("125.50"),db.queryForObject("SELECT price FROM penny_catalog_products WHERE id=?",java.math.BigDecimal.class,id));
  assertEquals("Distributor",db.queryForObject("SELECT supplier FROM penny_catalog_products WHERE id=?",String.class,id));
  mvc.perform(put("/api/catalog-products/"+id).principal(admin).contentType(APPLICATION_JSON).content(catalog("COMM","INTERNAL").replace("\"active\":true","\"active\":true,\"supplier\":null,\"price\":null"))).andExpect(status().isOk());
  assertNull(db.queryForObject("SELECT price FROM penny_catalog_products WHERE id=?",java.math.BigDecimal.class,id));
  assertNull(db.queryForObject("SELECT supplier FROM penny_catalog_products WHERE id=?",String.class,id));
  assertEquals(3,db.queryForObject("SELECT count(*) FROM penny_m2_audit WHERE entity_type='CATALOG_PRODUCT' AND entity_id=?",Integer.class,id));
 }
 @Test void rejectsInvalidPriceAndAllowsCatalogTrainingOnly() throws Exception {
  for(String price:new String[]{"-1","1000000000","0.001","999999999.99000001","true","{}"})mvc.perform(post("/api/catalog-products").principal(admin).contentType(APPLICATION_JSON).content(catalog("BAD","INTERNAL").replace("\"active\":true","\"active\":true,\"price\":"+price))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/catalog-products").principal(admin).contentType(APPLICATION_JSON).content(catalog("COURSE","INTERNAL").replace("SOFTWARE","TRAINING"))).andExpect(status().isOk());
  mvc.perform(get("/api/catalog-products").principal(tech)).andExpect(jsonPath("$.total").value(1));
  mvc.perform(get("/api/catalog-products").principal(tech).param("installable","false")).andExpect(jsonPath("$.total").value(1));
  mvc.perform(get("/api/catalog-products").principal(tech).param("installable","true")).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items").isEmpty());
  long id=db.queryForObject("SELECT id FROM penny_catalog_products WHERE code='COURSE'",Long.class);
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",id).replace("SOFTWARE","TRAINING"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"catalogProductId\":"+id+",\"soldOn\":\"2026-10-26\",\"quantity\":1,\"unitPrice\":100}")).andExpect(status().isOk());
  mvc.perform(get("/api/inventory").principal(admin)).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.totals.availableQuantity").value(0));
  mvc.perform(get("/api/sales").principal(admin)).andExpect(jsonPath("$.total").value(1));
 }
 @Test void migrationPreservesCatalogAndInstalledRows(){
  String schema="commercial_"+UUID.randomUUID().toString().replace("-","");
  var source=new DriverManagerDataSource(URL+"?currentSchema="+schema,USER,PASSWORD);
  try{
   Flyway.configure().dataSource(source).schemas(schema).target("109").load().migrate();
   var legacy=new JdbcTemplate(source);
   legacy.update("INSERT INTO penny_catalog_products(id,code,name,category,origin,manufacturer) VALUES (42,'LEG','Legacy','SOFTWARE','INTERNAL','Maker')");
   legacy.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Legacy customer')");
   legacy.update("INSERT INTO penny_installed_products(id,customer_id,category,name,origin,catalog_product_id,serial_number) VALUES (42,1,'SOFTWARE','Installed legacy','INTERNAL',42,'SER-42')");
   var installedBefore=legacy.queryForMap("SELECT * FROM penny_installed_products WHERE id=42");
   var before=legacy.queryForMap("SELECT * FROM penny_catalog_products WHERE id=42");
   Flyway.configure().dataSource(source).schemas(schema).target("110").load().migrate();
   var after=legacy.queryForMap("SELECT * FROM penny_catalog_products WHERE id=42");
   assertEquals(installedBefore,legacy.queryForMap("SELECT * FROM penny_installed_products WHERE id=42"));
   assertThrows(DataIntegrityViolationException.class,()->legacy.update("UPDATE penny_installed_products SET category='TRAINING' WHERE id=42"));
   assertNull(after.remove("supplier"));assertNull(after.remove("price"));assertEquals(before,after);
   legacy.update("INSERT INTO penny_catalog_products(code,name,category,origin) VALUES ('COURSE','Course','TRAINING','INTERNAL')");
   assertThrows(DataIntegrityViolationException.class,()->legacy.update("UPDATE penny_catalog_products SET price=-1 WHERE id=42"));
  }finally{db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
 }
}
