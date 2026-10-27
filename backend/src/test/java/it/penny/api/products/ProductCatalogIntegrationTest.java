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
class ProductCatalogIntegrationTest {
 static final String URL=System.getenv("PENNY_TEST_JDBC_URL");
 static final String USER=System.getenv("PENNY_TEST_DB_USER");
 static final String PASSWORD=System.getenv("PENNY_TEST_DB_PASSWORD");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->URL+"?currentSchema=m106_api");
  p.add("spring.datasource.username",()->USER);p.add("spring.datasource.password",()->PASSWORD);
  p.add("spring.flyway.schemas",()->"m106_api");p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
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
 @Test void catalogCrudValidationSearchAndPermissions() throws Exception {
  mvc.perform(post("/api/catalog-products").principal(tech).contentType(APPLICATION_JSON).content(catalog("TEST","INTERNAL"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/catalog-products").principal(admin).contentType(APPLICATION_JSON).content(catalog("TEST","INTERNAL"))).andExpect(status().isOk());
  mvc.perform(post("/api/catalog-products").principal(admin).contentType(APPLICATION_JSON).content(catalog("test","INTERNAL"))).andExpect(status().isConflict());
  mvc.perform(get("/api/catalog-products").principal(tech)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
  mvc.perform(get("/api/catalog-products").param("q","%").principal(admin)).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/api/catalog-products").param("size","101").principal(admin)).andExpect(status().isBadRequest());
  mvc.perform(post("/api/catalog-products").principal(admin).contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
  mvc.perform(get("/api/catalog-products")).andExpect(status().isForbidden());
 }
 @Test void internalLinkRequiredAndExternalOptional() throws Exception {
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",null))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("THIRD_PARTY",null))).andExpect(status().isOk());
  long id=entry("INT","INTERNAL");
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",id))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products").param("sort","createdAt").param("direction","desc").principal(admin)).andExpect(jsonPath("$.items[0].origin").value("INTERNAL")).andExpect(jsonPath("$.items[0].catalogProductId").value(id)).andExpect(jsonPath("$.items[0].name").value("Snapshot personalizzato"));
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("THIRD_PARTY",id))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",id).replace("SOFTWARE","HARDWARE"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",99999L))).andExpect(status().isBadRequest());
 }
 @Test void archivedLinksRemainButCannotBeNewlyAssigned() throws Exception {
  long id=entry("INT","INTERNAL");
  long installed=db.queryForObject("INSERT INTO penny_installed_products(customer_id,category,name,origin,catalog_product_id) VALUES (1,'SOFTWARE','Originale','INTERNAL',?) RETURNING id",Long.class,id);
  mvc.perform(put("/api/catalog-products/"+id).principal(admin).contentType(APPLICATION_JSON).content(catalog("INT","INTERNAL").replace("true","false"))).andExpect(status().isOk());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",id))).andExpect(status().isBadRequest());
  mvc.perform(put("/api/installed-products/"+installed).principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",id))).andExpect(status().isOk());
  mvc.perform(put("/api/catalog-products/"+id).principal(admin).contentType(APPLICATION_JSON).content(catalog("INT","THIRD_PARTY"))).andExpect(status().isConflict());
  assertThrows(DataIntegrityViolationException.class,()->db.update("DELETE FROM penny_catalog_products WHERE id=?",id));
 }
 @Test void catalogReadDenyBlocksNewLinkButAllowsExistingSnapshotEdit() throws Exception {
  long id=entry("INT","INTERNAL");
  long installed=db.queryForObject("INSERT INTO penny_installed_products(customer_id,category,name,origin,catalog_product_id) VALUES (1,'SOFTWARE','Originale','INTERNAL',?) RETURNING id",Long.class,id);
  db.update("INSERT INTO penny_user_permissions SELECT id,'CATALOG_READ','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(get("/api/catalog-products").principal(admin)).andExpect(status().isForbidden());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",id))).andExpect(status().isForbidden());
  mvc.perform(put("/api/installed-products/"+installed).principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",id))).andExpect(status().isOk());
 }
 @Test void databaseRejectsInvalidAssociations(){
  long id=entry("INT","INTERNAL");
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_installed_products(customer_id,category,name,origin) VALUES (1,'SOFTWARE','Invalid','INTERNAL')"));
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_installed_products(customer_id,category,name,origin,catalog_product_id) VALUES (1,'HARDWARE','Invalid','INTERNAL',?)",id));
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_installed_products(customer_id,category,name,origin,catalog_product_id) VALUES (1,'SOFTWARE','Invalid','THIRD_PARTY',?)",id));
 }
 @Test void thirdPartyLinksAndSnapshotIndependence() throws Exception {
  long id=entry("EXT","THIRD_PARTY");
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("THIRD_PARTY",id))).andExpect(status().isOk());
  mvc.perform(put("/api/catalog-products/"+id).principal(admin).contentType(APPLICATION_JSON).content(catalog("EXT","THIRD_PARTY").replace("Prodotto","Nuovo nome"))).andExpect(status().isOk());
  mvc.perform(get("/api/catalog-products/"+id).principal(tech)).andExpect(jsonPath("$.name").value("Nuovo nome"));
  mvc.perform(get("/api/installed-products").principal(admin)).andExpect(jsonPath("$.items[0].name").value("Snapshot personalizzato"));
  mvc.perform(put("/api/catalog-products/"+id).principal(admin).contentType(APPLICATION_JSON).content(catalog("EXT","THIRD_PARTY").replace("SOFTWARE","HARDWARE"))).andExpect(status().isConflict());
 }
 @Test void permissionOverridesAndAccountGate() throws Exception {
  db.update("INSERT INTO penny_user_permissions SELECT id,'CATALOG_WRITE','ALLOW' FROM penny_users WHERE email=?",tech.getName());
  mvc.perform(post("/api/catalog-products").principal(tech).contentType(APPLICATION_JSON).content(catalog("OVERRIDE","INTERNAL"))).andExpect(status().isOk());
  db.update("INSERT INTO penny_user_permissions SELECT id,'PRODUCTS_WRITE','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("THIRD_PARTY",null))).andExpect(status().isForbidden());
  mvc.perform(get("/api/auth/me").principal(admin)).andExpect(jsonPath("$.canReadCatalog").value(true)).andExpect(jsonPath("$.canWriteCatalog").value(true)).andExpect(jsonPath("$.canWriteProducts").value(false));
  db.update("UPDATE penny_users SET enabled=false WHERE email=?",tech.getName());
  mvc.perform(get("/api/catalog-products").principal(tech)).andExpect(status().isForbidden());
  db.update("UPDATE penny_users SET must_change_password=true WHERE email=?",admin.getName());
  mvc.perform(get("/api/catalog-products").principal(admin)).andExpect(status().isForbidden());
 }
 @Test void invalidAndMissingInputsAreRejected() throws Exception {
  for(String body:new String[]{"{}",installed("UNKNOWN",null),installed("THIRD_PARTY",null).replace("SOFTWARE","UNKNOWN")}){
   mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
  }
  mvc.perform(get("/api/catalog-products/999999").principal(admin)).andExpect(status().isNotFound());
  mvc.perform(get("/api/catalog-products").principal(admin).param("origin","UNKNOWN")).andExpect(status().isBadRequest());
 }
 @Test void archivingSerializesWithNewLink() throws Exception {
  long id=entry("RACE","INTERNAL");
  try(var connection=java.util.Objects.requireNonNull(db.getDataSource()).getConnection()){
   connection.setAutoCommit(false);
   try(var statement=connection.prepareStatement("UPDATE penny_catalog_products SET active=false WHERE id=?")){statement.setLong(1,id);statement.executeUpdate();}
   var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
   try{
    var started=new java.util.concurrent.CountDownLatch(1);
    var result=executor.submit(()->{started.countDown();return mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(installed("INTERNAL",id))).andReturn().getResponse().getStatus();});
    assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));
    assertThrows(java.util.concurrent.TimeoutException.class,()->result.get(200,java.util.concurrent.TimeUnit.MILLISECONDS));
    connection.commit();assertEquals(400,result.get(5,java.util.concurrent.TimeUnit.SECONDS));
   }finally{connection.rollback();executor.shutdownNow();}
  }
 }
 @Test void migrationPreservesLegacyData(){
  String schema="legacy_"+UUID.randomUUID().toString().replace("-","");
  var source=new DriverManagerDataSource(URL+"?currentSchema="+schema,USER,PASSWORD);
  try{
   Flyway.configure().dataSource(source).schemas(schema).target("103").load().migrate();
   var legacy=new JdbcTemplate(source);
   legacy.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Legacy')");
   legacy.update("INSERT INTO penny_installed_products(id,customer_id,category,name,serial_number,notes) VALUES (42,1,'HARDWARE','Legacy name','SER-42','Dati da conservare')");
   var before=legacy.queryForMap("SELECT * FROM penny_installed_products WHERE id=42");
   Flyway.configure().dataSource(source).schemas(schema).target("104").load().migrate();
   var after=legacy.queryForMap("SELECT * FROM penny_installed_products WHERE id=42");
   assertEquals("THIRD_PARTY",after.remove("origin"));assertNull(after.remove("catalog_product_id"));assertEquals(before,after);
   assertEquals(0,legacy.queryForObject("SELECT count(*) FROM penny_catalog_products",Integer.class));
  }finally{db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
 }
}
