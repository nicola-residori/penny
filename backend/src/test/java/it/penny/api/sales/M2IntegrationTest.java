package it.penny.api.sales;

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
import org.springframework.security.web.FilterChainProxy;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(classes=PennyApplication.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class M2IntegrationTest {
 static final String URL=System.getenv("PENNY_TEST_JDBC_URL"),USER=System.getenv("PENNY_TEST_DB_USER"),PASSWORD=System.getenv("PENNY_TEST_DB_PASSWORD");
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->URL+"?currentSchema=m2_api");p.add("spring.datasource.username",()->USER);p.add("spring.datasource.password",()->PASSWORD);p.add("spring.flyway.schemas",()->"m2_api");p.add("spring.jpa.hibernate.ddl-auto",()->"validate");}
 @Autowired JdbcTemplate db;@Autowired WebApplicationContext context;@Autowired ObjectMapper json;@Autowired FilterChainProxy security;
 MockMvc mvc;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("m2-admin@test.invalid","unused",List.of(new SimpleGrantedAuthority("ROLE_USER")));
 final UsernamePasswordAuthenticationToken tech=new UsernamePasswordAuthenticationToken("m2-tech@test.invalid","unused",List.of(new SimpleGrantedAuthority("ROLE_USER")));
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).build();
  db.execute("TRUNCATE penny_sales,penny_workstations,penny_installed_products,penny_catalog_products,penny_customers,penny_users,penny_m2_audit RESTART IDENTITY CASCADE");
  for(String role:new String[]{"ADMIN","TECH","SALES"}){String email="m2-"+role.toLowerCase()+"@test.invalid";db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,?,'unused',false)",email,role);db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code=?",email,role);}
  db.update("INSERT INTO penny_customers(id,kind,name,lifecycle_status) VALUES (1,'COMPANY','Alpha','CUSTOMER'),(2,'COMPANY','Beta','CUSTOMER')");
  db.update("INSERT INTO penny_catalog_products(id,code,name,origin,category) VALUES (1,'PEN-01','Penny Suite','INTERNAL','SOFTWARE'),(2,'EXT-01','Hardware','THIRD_PARTY','HARDWARE')");
  db.update("INSERT INTO penny_installed_products(id,customer_id,category,name,origin,catalog_product_id) VALUES (1,1,'SOFTWARE','Ufficio Alpha','INTERNAL',1),(2,1,'SOFTWARE','Altro Alpha','INTERNAL',1),(3,2,'SOFTWARE','Beta','INTERNAL',1),(4,1,'HARDWARE','Monitor','THIRD_PARTY',2)");
  db.queryForObject("SELECT setval(pg_get_serial_sequence('penny_installed_products','id'),(SELECT max(id) FROM penny_installed_products))",Long.class);
 }
 String sale(int quantity,String price){return "{\"customerId\":1,\"catalogProductId\":1,\"soldOn\":\"2026-10-25\",\"quantity\":"+quantity+",\"unitPrice\":"+price+",\"reference\":\"ORD-1\"}";}
 long createSale(int quantity) throws Exception {var r=mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content(sale(quantity,"12.50"))).andExpect(status().isOk()).andReturn();return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();}
 long allocate(long sale,long installed,int quantity) throws Exception {var r=mvc.perform(post("/api/allocations").principal(tech).contentType(APPLICATION_JSON).content("{\"saleId\":"+sale+",\"installedProductId\":"+installed+",\"quantity\":"+quantity+"}")).andExpect(status().isOk()).andReturn();return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();}
 @Test void orderSearchHonorsCustomerPeriodStatusAndPagination()throws Exception{
  db.update("INSERT INTO penny_sales_orders(customer_id,order_number,sold_on,status) VALUES (1,'FILTER-A','2026-10-01','DRAFT'),(2,'FILTER-B','2026-10-02','CONFIRMED'),(1,'FILTER-C','2026-10-03','CONFIRMED')");
  mvc.perform(get("/api/sales-orders").principal(admin).param("q","FILTER").param("customerId","1").param("from","2026-10-02").param("to","2026-10-31").param("status","CONFIRMED").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].orderNumber").value("FILTER-C"));
  mvc.perform(get("/api/sales-orders").principal(admin).param("q","FILTER").param("page","1").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items[0].orderNumber").value("FILTER-B"));
  mvc.perform(get("/api/sales-orders").principal(admin).param("status","UNKNOWN")).andExpect(status().isBadRequest());
 }
 @Test void mixedOrderConfirmationIsIdempotentAndKeepsExactLineReferences() throws Exception {
  String body="""
   {"customerId":1,"orderNumber":"123/26","soldOn":"2026-10-29","lines":[
    {"catalogProductId":2,"quantity":3,"unitPrice":"3200.00","business":{"operationType":"RENTAL","durationMonths":12,"warrantyMonths":12}},
    {"catalogProductId":1,"quantity":1,"unitPrice":"2930.00","business":{"licenseType":"ANNUAL","operationType":"NEW_LICENSE","durationMonths":12,"soldRelease":"R1"}}
   ]}
   """;
  var response=mvc.perform(post("/api/sales-orders").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
  long order=json.readTree(response.getResponse().getContentAsString()).path("id").asLong();
  assertEquals(2,db.queryForObject("SELECT count(*) FROM penny_sales WHERE order_id=? AND status='DRAFT'",Integer.class,order));
  assertEquals(0,db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE sale_id IS NOT NULL",Integer.class));
  mvc.perform(post("/api/sales-orders/"+order+"/confirm").principal(admin)).andExpect(status().isOk());
  mvc.perform(post("/api/sales-orders/"+order+"/confirm").principal(admin)).andExpect(status().isOk());
  assertEquals(4,db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE sale_id IN (SELECT id FROM penny_sales WHERE order_id=?)",Integer.class,order));
  assertEquals(4,db.queryForObject("SELECT count(*) FROM penny_sale_allocations a JOIN penny_sales s ON s.id=a.sale_id WHERE s.order_id=?",Integer.class,order));
  long line=db.queryForObject("SELECT id FROM penny_sales WHERE order_id=? AND catalog_product_id=1",Long.class,order);
  assertFalse(json.readTree(db.queryForObject("SELECT business::text FROM penny_sales WHERE id=?",String.class,line)).has("expiresOn"));
  mvc.perform(put("/api/sales-orders/"+order+"/lines/"+line+"/operations").principal(admin).contentType(APPLICATION_JSON).content("{\"licenseId\":\"KEY-123\",\"effectiveFrom\":\"2026-11-15\"}")).andExpect(status().isOk());
  var business=json.readTree(db.queryForObject("SELECT business::text FROM penny_sales WHERE id=?",String.class,line));
  assertEquals("KEY-123",db.queryForObject("SELECT license_key FROM penny_installed_products WHERE sale_id=?",String.class,line));assertEquals("KEY-123",business.path("licenseId").asText());assertEquals("2027-11-15",business.path("expiresOn").asText());
  assertEquals("NEW_LICENSE",business.path("operationType").asText());
  var installed=json.readTree(db.queryForObject("SELECT business::text FROM penny_installed_products WHERE sale_id=?",String.class,line));
  assertEquals("ANNUAL",installed.path("licenseType").asText());assertEquals("NEW_LICENSE",installed.path("softwareSaleType").asText());assertEquals("R1",installed.path("soldRelease").asText());assertFalse(installed.has("operationalKind"));assertFalse(installed.has("expiresOn"));
  assertEquals(java.time.LocalDate.of(2027,11,15),db.queryForObject("SELECT license_expires_on FROM penny_installed_products WHERE sale_id=?",java.time.LocalDate.class,line));
  mvc.perform(post("/api/sales-orders/"+order+"/confirm").principal(admin)).andExpect(status().isOk());
  assertEquals("KEY-123",db.queryForObject("SELECT license_key FROM penny_installed_products WHERE sale_id=?",String.class,line));
 }
 @Test void orderContractDateUpdatesKeepCorrectionHistoryOnlyWhenDatesChange() throws Exception {
  db.update("INSERT INTO penny_catalog_products(id,code,name,origin,category,category_id,product_type_id) SELECT 3,'SUPPORT','Supporto','THIRD_PARTY','SERVICE',c.id,(SELECT min(id) FROM penny_product_types WHERE category_id=c.id) FROM penny_product_categories c WHERE c.code='CONTRACT'");
  String body="""
   {"customerId":1,"orderNumber":"SUP-26","soldOn":"2026-10-29","lines":[{"catalogProductId":3,"quantity":1,"unitPrice":"100","business":{"durationMonths":12}}]}
   """;
  var response=mvc.perform(post("/api/sales-orders").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
  long order=json.readTree(response.getResponse().getContentAsString()).path("id").asLong();
  mvc.perform(post("/api/sales-orders/"+order+"/confirm").principal(admin)).andExpect(status().isOk());
  long line=db.queryForObject("SELECT id FROM penny_sales WHERE order_id=?",Long.class,order);
  long record=db.queryForObject("SELECT id FROM penny_customer_records WHERE sale_id=?",Long.class,line);
  String path="/api/sales-orders/"+order+"/lines/"+line+"/operations";
  mvc.perform(put(path).principal(admin).contentType(APPLICATION_JSON).content("{\"effectiveFrom\":\"2026-11-15\"}")).andExpect(status().isOk());
  mvc.perform(put(path).principal(admin).contentType(APPLICATION_JSON).content("{\"effectiveFrom\":\"2026-11-15\"}")).andExpect(status().isOk());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_contract_renewals WHERE record_id=?",Integer.class,record));
  mvc.perform(put(path).principal(admin).contentType(APPLICATION_JSON).content("{\"effectiveFrom\":\"2026-12-01\"}")).andExpect(status().isOk());
  var events=db.queryForList("SELECT * FROM penny_contract_renewals WHERE record_id=? ORDER BY id",record);
  assertEquals(2,events.size());assertEquals("CORRECTION",events.get(1).get("event_type"));assertEquals(admin.getName(),events.get(1).get("actor"));assertEquals("Aggiornamento date dalla riga ordine",events.get(1).get("reason"));
  assertEquals("2026-11-15",events.get(1).get("previous_start").toString());assertEquals("2027-11-15",events.get(1).get("previous_end").toString());assertEquals("2026-12-01",events.get(1).get("new_start").toString());assertEquals("2027-12-01",events.get(1).get("new_end").toString());
 }
 @Test void legacySaleRetainsLedgerIdAndGetsSingleOrderHeader() throws Exception {
  long line=createSale(2);assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_sales s JOIN penny_sales_orders o ON o.id=s.order_id AND o.customer_id=s.customer_id WHERE s.id=? AND o.status='CONFIRMED'",Integer.class,line));
 }
 @Test void orderFinancialDataRequiresSalesRead() throws Exception {
  mvc.perform(get("/api/sales-orders").principal(tech)).andExpect(status().isForbidden());
  mvc.perform(post("/api/sales-orders").principal(tech).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
 }
 @Test void salesHistorySummaryAndCancellation() throws Exception {
  long id=createSale(10);createSale(2);
  mvc.perform(get("/api/sales/summary").principal(admin)).andExpect(jsonPath("$.totals.soldQuantity").value(12)).andExpect(jsonPath("$.totals.totalAmount").value("150.00")).andExpect(jsonPath("$.items[0].customerName").value("Alpha"));
  mvc.perform(post("/api/sales/"+id+"/cancel").principal(admin).contentType(APPLICATION_JSON).content("{\"reason\":\"Registrazione errata\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/sales/"+id).principal(admin)).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancellationReason").value("Registrazione errata"));
  mvc.perform(get("/api/sales/summary").principal(admin)).andExpect(jsonPath("$.totals.soldQuantity").value(2)).andExpect(jsonPath("$.totals.totalAmount").value("25.00"));
  mvc.perform(get("/api/sales").principal(admin).param("status","CANCELLED")).andExpect(jsonPath("$.total").value(1));
  mvc.perform(post("/api/sales/"+id+"/cancel").principal(admin).contentType(APPLICATION_JSON).content("{\"reason\":\"Doppio\"}")).andExpect(status().isConflict());
  assertEquals(3,db.queryForObject("SELECT count(*) FROM penny_m2_audit",Integer.class));
 }
 @Test void exactMoneySnapshotAndValidation() throws Exception {
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content(sale(3,"0.10"))).andExpect(status().isOk());
  db.update("UPDATE penny_catalog_products SET name='Nuovo nome' WHERE id=1");
  mvc.perform(get("/api/sales").principal(admin)).andExpect(jsonPath("$.items[0].totalAmount").value("0.30")).andExpect(jsonPath("$.items[0].productName").value("Penny Suite"));
  for(String body:new String[]{"{}",sale(0,"1"),sale(-1,"1"),sale(1,"-1"),sale(1,"0.001"),sale(1,"1000000000"),sale(1000001,"1")})mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/sales").principal(admin).param("from","2026-12-01").param("to","2026-01-01")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/sales").principal(admin).param("q","%")).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/api/sales/summary").principal(admin).param("from","2027-01-01")).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.totals.totalAmount").value("0.00"));
 }
 @Test void allocationsPreserveSoldAndReleaseAvailability() throws Exception {
  long id=createSale(5),a=allocate(id,1,3);
  mvc.perform(get("/api/inventory").principal(tech)).andExpect(jsonPath("$.items[0].quantity").value(5)).andExpect(jsonPath("$.items[0].allocatedQuantity").value(3)).andExpect(jsonPath("$.items[0].availableQuantity").value(2)).andExpect(jsonPath("$.items[0].unitPrice").doesNotExist()).andExpect(jsonPath("$.totals.totalAmount").doesNotExist());
  mvc.perform(put("/api/allocations/"+a).principal(tech).contentType(APPLICATION_JSON).content("{\"quantity\":4}")).andExpect(status().isOk());
  mvc.perform(post("/api/sales/"+id+"/cancel").principal(admin).contentType(APPLICATION_JSON).content("{\"reason\":\"Errore\"}")).andExpect(status().isConflict());
  mvc.perform(delete("/api/allocations/"+a).principal(tech)).andExpect(status().isOk());
  mvc.perform(get("/api/inventory").principal(tech)).andExpect(jsonPath("$.totals.soldQuantity").value(5)).andExpect(jsonPath("$.totals.availableQuantity").value(5));
  assertEquals(4,db.queryForObject("SELECT count(*) FROM penny_m2_audit",Integer.class));
 }
 @Test void allocationLimitsAndCrossCustomerAreRejected() throws Exception {
  long id=createSale(3);allocate(id,1,2);
  for(long installed:new long[]{2,3,4})mvc.perform(post("/api/allocations").principal(tech).contentType(APPLICATION_JSON).content("{\"saleId\":"+id+",\"installedProductId\":"+installed+",\"quantity\":2}")).andExpect(status().isConflict());
  assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_sale_allocations(sale_id,installed_product_id,customer_id,catalog_product_id,quantity) VALUES (?,2,1,1,2)",id));
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_installed_products SET status='RETIRED' WHERE id=1"));
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_installed_products SET customer_id=2 WHERE id=1"));
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_sales SET quantity=1 WHERE id=?",id));
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_sales SET status='CANCELLED',cancellation_reason='SQL' WHERE id=?",id));
 }
 @Test void concurrentAllocationCannotOversell() throws Exception {
  long id=createSale(3);var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{
   List<Future<Integer>> results=new ArrayList<>();
   for(int installed:new int[]{1,2})results.add(executor.submit(()->{start.await();return mvc.perform(post("/api/allocations").principal(tech).contentType(APPLICATION_JSON).content("{\"saleId\":"+id+",\"installedProductId\":"+installed+",\"quantity\":2}")).andReturn().getResponse().getStatus();}));
   start.countDown();var statuses=new ArrayList<Integer>();for(var result:results)statuses.add(result.get(10,TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
   assertEquals(2,db.queryForObject("SELECT sum(quantity) FROM penny_sale_allocations WHERE sale_id=?",Integer.class,id));
  }finally{executor.shutdownNow();}
 }
 @Test void workstationAssociationsAndArchive() throws Exception {
  mvc.perform(post("/api/workstations").principal(tech).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"name\":\"PC Direzione\",\"location\":\"Piano 1\",\"active\":true}")).andExpect(status().isOk());
  long station=db.queryForObject("SELECT id FROM penny_workstations",Long.class);
  String installed="{\"customerId\":1,\"category\":\"SOFTWARE\",\"name\":\"Suite\",\"origin\":\"INTERNAL\",\"catalogProductId\":1,\"workstationId\":"+station+"}";
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(installed)).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(tech)).andExpect(jsonPath("$.workstationName").value("PC Direzione"));
  assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE penny_installed_products SET workstation_id=? WHERE id=3",station));
  mvc.perform(put("/api/workstations/"+station).principal(tech).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"name\":\"PC Direzione\",\"active\":false}")).andExpect(status().isOk());
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(installed)).andExpect(status().isOk());
  mvc.perform(put("/api/installed-products/2").principal(admin).contentType(APPLICATION_JSON).content(installed)).andExpect(status().isBadRequest());
  mvc.perform(put("/api/workstations/"+station).principal(tech).contentType(APPLICATION_JSON).content("{\"customerId\":2,\"name\":\"Spostata\",\"active\":true}")).andExpect(status().isConflict());
 }
 @Test void permissionsHideFinancialDataAndOverview() throws Exception {
  createSale(3);
  mvc.perform(get("/api/sales").principal(tech)).andExpect(status().isForbidden());
  mvc.perform(get("/api/sales/summary").principal(tech)).andExpect(status().isForbidden());
  mvc.perform(get("/api/overview").principal(tech)).andExpect(jsonPath("$.salesAmount").isEmpty()).andExpect(jsonPath("$.soldQuantity").value(3));
  mvc.perform(get("/api/auth/me").principal(tech)).andExpect(jsonPath("$.canReadSales").value(false)).andExpect(jsonPath("$.canWriteAllocations").value(true));
  db.update("INSERT INTO penny_user_permissions SELECT id,'ALLOCATIONS_WRITE','DENY' FROM penny_users WHERE email=?",tech.getName());
  mvc.perform(post("/api/allocations").principal(tech).contentType(APPLICATION_JSON).content("{\"saleId\":1,\"installedProductId\":1,\"quantity\":1}")).andExpect(status().isForbidden());
  db.update("INSERT INTO penny_user_permissions SELECT id,'INVENTORY_READ','DENY' FROM penny_users WHERE email=?",tech.getName());
  mvc.perform(get("/api/inventory").principal(tech)).andExpect(status().isForbidden());
 }
 @Test void fullSecurityRequiresSessionAndCsrf() throws Exception {
  MockMvc secured=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
  secured.perform(get("/api/sales")).andExpect(status().isUnauthorized());
  var session=new MockHttpSession();session.setAttribute("SPRING_SECURITY_CONTEXT",new SecurityContextImpl(admin));session.setAttribute("PENNY_SESSION_VERSION",0L);
  secured.perform(post("/api/sales").session(session).contentType(APPLICATION_JSON).content(sale(1,"1.00"))).andExpect(status().isForbidden());
  var response=secured.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn().getResponse();
  String token=json.readTree(response.getContentAsString()).get("token").asText();
  secured.perform(post("/api/sales").session(session).cookie(response.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN",token).contentType(APPLICATION_JSON).content(sale(1,"1.00"))).andExpect(status().isOk());
 }
 @Test void decimalQuantitiesAreNeverSilentlyTruncated() throws Exception {
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content(sale(1,"1.00").replace("\"quantity\":1,","\"quantity\":1.5,"))).andExpect(status().isBadRequest());
  long id=createSale(3);
  mvc.perform(post("/api/allocations").principal(tech).contentType(APPLICATION_JSON).content("{\"saleId\":"+id+",\"installedProductId\":1,\"quantity\":1.5}")).andExpect(status().isBadRequest());
 }
 @Test void workstationRemovalNeedsReadAndIsAudited() throws Exception {
  long station=db.queryForObject("INSERT INTO penny_workstations(customer_id,name) VALUES (1,'Desk') RETURNING id",Long.class);
  String installed="{\"customerId\":1,\"category\":\"SOFTWARE\",\"name\":\"Suite\",\"origin\":\"INTERNAL\",\"catalogProductId\":1,\"workstationId\":"+station+"}";
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(installed)).andExpect(status().isOk());
  assertEquals(1,db.queryForObject("SELECT count(*) FROM penny_m2_audit WHERE action='INSTALLATION_WORKSTATION_CHANGED'",Integer.class));
  db.update("INSERT INTO penny_user_permissions SELECT id,'WORKSTATIONS_READ','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(installed)).andExpect(status().isOk());
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(installed.replace("\"workstationId\":"+station,"\"workstationId\":null"))).andExpect(status().isForbidden());
 }
 @Test void archivedCatalogBlocksNewOrIncreasedAllocationsButAllowsRelease() throws Exception {
  long id=createSale(5),a=allocate(id,1,2);db.update("UPDATE penny_catalog_products SET active=false WHERE id=1");
  mvc.perform(post("/api/allocations").principal(tech).contentType(APPLICATION_JSON).content("{\"saleId\":"+id+",\"installedProductId\":2,\"quantity\":1}")).andExpect(status().isConflict());
  mvc.perform(put("/api/allocations/"+a).principal(tech).contentType(APPLICATION_JSON).content("{\"quantity\":3}")).andExpect(status().isConflict());
  mvc.perform(put("/api/allocations/"+a).principal(tech).contentType(APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isOk());
  mvc.perform(delete("/api/allocations/"+a).principal(tech)).andExpect(status().isOk());
 }
 @Test void repeatableReadCannotOverallocateOrRetireAgainstOldSnapshot() throws Exception {
  long id=createSale(3);
  try(var older=db.getDataSource().getConnection()){
   older.setAutoCommit(false);older.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
   try(var statement=older.createStatement()){statement.executeQuery("SELECT count(*) FROM penny_sale_allocations").close();}
   allocate(id,1,2);
   var error=assertThrows(java.sql.SQLException.class,()->{try(var statement=older.prepareStatement("INSERT INTO penny_sale_allocations(sale_id,installed_product_id,customer_id,catalog_product_id,quantity) VALUES (?,2,1,1,2)")){statement.setLong(1,id);statement.executeUpdate();}});
   assertEquals("40001",error.getSQLState());older.rollback();
  }
  try(var older=db.getDataSource().getConnection()){
   older.setAutoCommit(false);older.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
   try(var statement=older.createStatement()){statement.executeQuery("SELECT count(*) FROM penny_sale_allocations").close();}
   allocate(id,2,1);
   var error=assertThrows(java.sql.SQLException.class,()->{try(var statement=older.createStatement()){statement.executeUpdate("UPDATE penny_installed_products SET status='RETIRED' WHERE id=2");}});
   assertEquals("40001",error.getSQLState());older.rollback();
  }
 }
 void analyticsSale(long customer,String date,int quantity,String price,String status){
  db.update("INSERT INTO penny_sales(customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price,status,cancellation_reason) VALUES (?,1,'PEN-01','Suite','INTERNAL','SOFTWARE',?::date,?,?,?,?)",customer,date,quantity,new java.math.BigDecimal(price),status,status.equals("CANCELLED")?"Errore":null);
 }
 @Test void analyticsCustomSpansAlignAndClipDailyAcquisition() throws Exception {
  analyticsSale(1,"2024-02-26",10,"1.00","CANCELLED");
  analyticsSale(1,"2024-02-27",100,"1.00","CONFIRMED");
  analyticsSale(2,"2024-02-28",2,"0.10","CONFIRMED");
  analyticsSale(2,"2024-03-01",3,"0.10","CONFIRMED");
  mvc.perform(get("/api/analytics").principal(admin).param("from","2024-02-28").param("to","2024-03-03").param("span","2d").param("groupBy","YEAR"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.span").value("2d")).andExpect(jsonPath("$.groupBy").value("DAY"))
   .andExpect(jsonPath("$.series.length()").value(3)).andExpect(jsonPath("$.series[0].period").value("2024-02-27")).andExpect(jsonPath("$.series[0].periodEnd").value("2024-02-28"))
   .andExpect(jsonPath("$.series[0].salesAmount").value("0.20")).andExpect(jsonPath("$.series[0].newCustomers").value(1)).andExpect(jsonPath("$.series[0].customersToDate").value(2))
   .andExpect(jsonPath("$.series[1].salesAmount").value("0.30")).andExpect(jsonPath("$.series[2].salesAmount").value("0.00")).andExpect(jsonPath("$.totals.salesAmount").value("0.50"));
  mvc.perform(get("/api/analytics").principal(admin).param("from","2024-02-28").param("to","2024-03-03").param("span","2d").param("customerId","1"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totals.salesAmount").value("0.00")).andExpect(jsonPath("$.totals.customersToDate").value(1));
 }
 @Test void analyticsCustomCalendarAndNegativeEpochSpans() throws Exception {
  for(String[] c:new String[][]{
   {"2w","2023-12-31","2024-01-02","WEEK","2023-12-25","2024-01-07"},
   {"3mon","2024-02-29","2024-03-01","MONTH","2024-01-01","2024-03-31"},
   {"2y","2025-02-28","2025-03-01","YEAR","2024-01-01","2025-12-31"},
   {"2d","1969-12-31","1969-12-31","DAY","1969-12-30","1969-12-31"},
   {"2w","1969-12-31","1969-12-31","WEEK","1969-12-22","1970-01-04"},
   {"3mon","1969-12-31","1969-12-31","MONTH","1969-10-01","1969-12-31"},
   {"2y","1969-12-31","1969-12-31","YEAR","1968-01-01","1969-12-31"}})
   mvc.perform(get("/api/analytics").principal(admin).param("span",c[0]).param("from",c[1]).param("to",c[2]))
    .andExpect(status().isOk()).andExpect(jsonPath("$.span").value(c[0])).andExpect(jsonPath("$.groupBy").value(c[3])).andExpect(jsonPath("$.series.length()").value(1))
    .andExpect(jsonPath("$.series[0].period").value(c[4])).andExpect(jsonPath("$.series[0].periodEnd").value(c[5]));
 }
 @Test void analyticsCustomSpanValidationAndActualBucketLimit() throws Exception {
  for(String span:new String[]{"","0d","01d","-1d","1D","1h","1m","1min","1.5d","10000d","999999999999999999999y"," 2d","2d ","201y","2401mon"})
   mvc.perform(get("/api/analytics").principal(admin).param("from","2024-01-01").param("to","2024-01-01").param("span",span)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/analytics").principal(admin).param("from","2024-01-01").param("to","2025-05-14").param("span","1d"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.series.length()").value(500));
  mvc.perform(get("/api/analytics").principal(admin).param("from","2024-01-01").param("to","2025-05-15").param("span","1d")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/analytics").principal(admin).param("from","1900-01-01").param("to","2100-12-31").param("span","200y").param("groupBy","MONTH"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.span").value("200y")).andExpect(jsonPath("$.series.length()").value(2));
  mvc.perform(get("/api/analytics").principal(tech).param("from","2024-01-01").param("to","2024-01-02").param("span","1d"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.series[0].salesAmount").isEmpty());
 }
 @Test void analyticsInclusivePartialLeapPeriodsAndAcquisitionHistory() throws Exception {
  analyticsSale(1,"2023-12-01",1,"1.00","CANCELLED");
  analyticsSale(1,"2024-02-14",50,"3.00","CONFIRMED");
  analyticsSale(1,"2024-02-15",2,"0.10","CONFIRMED");
  long allocated=db.queryForObject("SELECT max(id) FROM penny_sales",Long.class);allocate(allocated,1,1);allocate(allocated,2,1);
  analyticsSale(2,"2024-02-29",3,"0.10","CONFIRMED");
  analyticsSale(2,"2024-03-01",1,"0.10","CONFIRMED");
  analyticsSale(2,"2024-04-02",40,"1.00","CONFIRMED");
  db.update("INSERT INTO penny_customers(id,kind,name,lifecycle_status) VALUES (3,'COMPANY','Unknown','CUSTOMER'),(4,'COMPANY','Lead','PROSPECT')");
  db.update("UPDATE penny_customers SET archived=true WHERE id=2");
  db.update("UPDATE penny_customers SET deleted_at=now() WHERE id=3");
  mvc.perform(get("/api/analytics").principal(admin).param("from","2024-02-15").param("to","2024-04-01").param("groupBy","MONTH"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totals.salesAmount").value("0.60")).andExpect(jsonPath("$.totals.soldQuantity").value(6)).andExpect(jsonPath("$.totals.saleCount").value(3))
   .andExpect(jsonPath("$.totals.newCustomers").value(1)).andExpect(jsonPath("$.totals.customersToDate").value(2)).andExpect(jsonPath("$.totals.unknownCustomerDates").value(1))
   .andExpect(jsonPath("$.series.length()").value(3)).andExpect(jsonPath("$.series[0].period").value("2024-02-01")).andExpect(jsonPath("$.series[0].salesAmount").value("0.50"))
   .andExpect(jsonPath("$.series[0].customersToDate").value(2)).andExpect(jsonPath("$.series[2].salesAmount").value("0.00")).andExpect(jsonPath("$.series[2].customersToDate").value(2));
  mvc.perform(get("/api/analytics").principal(admin).param("from","2024-02-15").param("to","2024-04-01").param("customerId","1"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.groupBy").value("YEAR")).andExpect(jsonPath("$.totals.salesAmount").value("0.20")).andExpect(jsonPath("$.totals.newCustomers").value(0)).andExpect(jsonPath("$.totals.customersToDate").value(1));
  mvc.perform(get("/api/analytics").principal(admin).param("from","2024-01-01").param("to","2024-12-31").param("customerId","3")).andExpect(status().isNotFound());
 }
 @Test void analyticsPermissionsAndValidation() throws Exception {
  createSale(3);
  mvc.perform(get("/api/analytics").principal(tech).param("from","2026-01-01").param("to","2026-12-31"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.salesRead").value(false)).andExpect(jsonPath("$.totals.salesAmount").isEmpty()).andExpect(jsonPath("$.totals.soldQuantity").isEmpty()).andExpect(jsonPath("$.totals.saleCount").isEmpty()).andExpect(jsonPath("$.series[0].salesAmount").isEmpty()).andExpect(jsonPath("$.totals.newCustomers").value(1));
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_READ','DENY' FROM penny_users WHERE email=?",tech.getName());
  mvc.perform(get("/api/analytics").principal(tech).param("from","2026-01-01").param("to","2026-12-31")).andExpect(status().isForbidden());
  db.update("INSERT INTO penny_user_permissions SELECT id,'CUSTOMERS_READ','DENY' FROM penny_users WHERE email=?",admin.getName());
  mvc.perform(get("/api/analytics").principal(admin).param("from","2026-01-01").param("to","2026-12-31"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totals.salesAmount").value("37.50")).andExpect(jsonPath("$.totals.newCustomers").isEmpty()).andExpect(jsonPath("$.series[0].customersToDate").isEmpty());
  mvc.perform(get("/api/analytics").principal(admin).param("from","2026-01-01").param("to","2026-12-31").param("customerId","1")).andExpect(status().isForbidden());
  for(String[] range:new String[][]{{"1899-12-31","2026-01-01","YEAR"},{"2026-01-01","2101-01-01","YEAR"},{"2026-02-01","2026-01-01","YEAR"},{"2000-01-01","2010-01-01","MONTH"},{"2026-01-01","2026-12-31","WEEK"},{"oops","2026-12-31","YEAR"}})
   mvc.perform(get("/api/analytics").principal(admin).param("from",range[0]).param("to",range[1]).param("groupBy",range[2])).andExpect(status().isBadRequest());
  mvc.perform(get("/api/analytics").principal(admin)).andExpect(status().isBadRequest());
 }
 @Test void analyticsExactLargeCentsArchivedFilterAndEmptyDates() throws Exception {
  db.update("INSERT INTO penny_sales(customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price) SELECT 1,1,'PEN-01','Suite','INTERNAL','SOFTWARE','2026-01-01',1000000,999999999.99 FROM generate_series(1,1001)");
  analyticsSale(1,"2026-01-01",3,"0.10","CONFIRMED");
  db.update("UPDATE penny_customers SET archived=true WHERE id=1");
  mvc.perform(get("/api/analytics").principal(admin).param("from","2026-01-01").param("to","2026-01-01").param("customerId","1"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totals.salesAmount").value("1000999999989990000.30")).andExpect(jsonPath("$.totals.soldQuantity").value(1001000003L)).andExpect(jsonPath("$.totals.saleCount").value(1002));
  mvc.perform(get("/api/analytics").principal(admin).param("from","1900-01-01").param("to","2100-12-31"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.series.length()").value(201)).andExpect(jsonPath("$.series[0].salesAmount").value("0.00"));
  for(String id:new String[]{"0","-1"})mvc.perform(get("/api/analytics").principal(admin).param("from","2026-01-01").param("to","2026-12-31").param("customerId",id)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/analytics").principal(admin).param("from","2026-01-01").param("to","2026-12-31").param("customerId","999")).andExpect(status().isNotFound());
 }
 @Test void migrationPreservesM104Rows(){
  String schema="m2_legacy_"+UUID.randomUUID().toString().replace("-","");var source=new DriverManagerDataSource(URL+"?currentSchema="+schema,USER,PASSWORD);
  try{
   Flyway.configure().dataSource(source).schemas(schema).target("104").load().migrate();var legacy=new JdbcTemplate(source);
   legacy.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Legacy')");legacy.update("INSERT INTO penny_installed_products(id,customer_id,category,name,notes) VALUES (7,1,'HARDWARE','Conservare','Note storiche')");
   var before=legacy.queryForMap("SELECT * FROM penny_installed_products WHERE id=7");Flyway.configure().dataSource(source).schemas(schema).target("105").load().migrate();var after=legacy.queryForMap("SELECT * FROM penny_installed_products WHERE id=7");
   assertNull(after.remove("workstation_id"));assertEquals(0L,after.remove("allocation_version"));assertEquals(before,after);assertEquals(0,legacy.queryForObject("SELECT count(*) FROM penny_sales",Integer.class));
  }finally{db.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
 }
 @Test void saleCanRegisterAnInstallationAtomically() throws Exception {
  db.queryForObject("SELECT setval(pg_get_serial_sequence('penny_installed_products','id'),(SELECT max(id) FROM penny_installed_products))",Long.class);
  long product=db.queryForObject("SELECT id FROM penny_catalog_products ORDER BY id LIMIT 1",Long.class);
  int before=db.queryForObject("SELECT count(*) FROM penny_installed_products",Integer.class);
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"catalogProductId\":"+product+",\"soldOn\":\"2026-10-26\",\"quantity\":3,\"unitPrice\":\"29.90\",\"registerResource\":true}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.resourceId").isNumber());
  assertEquals(before+1,db.queryForObject("SELECT count(*) FROM penny_installed_products",Integer.class));
  assertEquals(3,db.queryForObject("SELECT quantity FROM penny_sale_allocations ORDER BY id DESC LIMIT 1",Integer.class));
 }
}
