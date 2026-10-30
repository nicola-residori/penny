package it.penny.api.products;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
class InstalledBusinessFieldsTest extends InstalledOperationalFieldsTest {
 @Test void businessDetailsRoundTripAndPreserveOmission() throws Exception {
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"business\":{\"usageRelease\":\"2026.1\",\"licenseType\":\"ANNUAL\",\"softwareStatus\":\"ACTIVE\",\"saleSource\":\"THIRD_PARTY\"}"))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.business.usageRelease").value("2026.1"));
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(input(""))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.business.usageRelease").value("2026.1"));
 }
 @Test void rejectsInvalidBusinessAndPerpetualExpiry() throws Exception {
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"business\":{\"softwareStatus\":123}"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"licenseExpiresOn\":\"2027-01-01\",\"business\":{\"licenseType\":\"PERPETUAL\"}"))).andExpect(status().isBadRequest());
 }
 @Test void calculatesLeapYearWarrantyAndKeepsExactPrices() throws Exception {
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"business\":{\"saleSource\":\"OUR_SALE\",\"soldOn\":\"2024-02-29\",\"salePrice\":\"123.45\",\"warrantyYears\":1,\"purchasePrice\":\"99.99\"}").replace("SOFTWARE","HARDWARE"))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.warrantyUntil").value("2025-02-28")).andExpect(jsonPath("$.business.salePrice").value("123.45"));
 }
 @Test void bulkRejectsUnknownScopeAtomically() throws Exception {
  mvc.perform(patch("/api/installed-products/bulk-release").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"typeId\":99999,\"ids\":[1],\"release\":\"2026\",\"changedOn\":\"2026-10-26\"}")).andExpect(status().isBadRequest());
 }
 @Test void bulkChangesAllSelectedReleasesAndRejectsMixedCustomerWithoutPartialWrites() throws Exception {
  Long type=db.queryForObject("SELECT t.id FROM penny_product_types t JOIN penny_product_categories c ON c.id=t.category_id WHERE c.operational_kind='SOFTWARE' AND NOT t.obsolete ORDER BY t.id LIMIT 1",Long.class);
  for(int n=0;n<2;n++)mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"productTypeId\":"+type+",\"business\":{\"usageRelease\":\"old\"}"))).andExpect(status().isOk());
  mvc.perform(patch("/api/installed-products/bulk-release").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"typeId\":"+type+",\"ids\":[2,1,2],\"release\":\"2027\",\"changedOn\":\"2026-10-25\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(2));
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.business.usageRelease").value("2027")).andExpect(jsonPath("$.business.releaseUpdatedOn").value("2026-10-25"));
  db.update("INSERT INTO penny_customers(id,kind,name) VALUES(2,'COMPANY','Other')");db.update("UPDATE penny_installed_products SET customer_id=2 WHERE id=2");
  mvc.perform(patch("/api/installed-products/bulk-release").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"typeId\":"+type+",\"ids\":[1,2],\"release\":\"bad\",\"changedOn\":\"2026-10-26\"}")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.business.usageRelease").value("2027"));
 }
 @Test void rejectsThirdPartyManualSaleAndSaleRentalExpiry() throws Exception {
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"business\":{\"saleSource\":\"THIRD_PARTY\",\"salePrice\":\"1.00\"}"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"business\":{\"saleType\":\"SALE\",\"expiresOn\":\"2027-01-01\"}").replace("SOFTWARE","HARDWARE"))).andExpect(status().isBadRequest());
 }

 @Test void linkedSalesKeepAuthoritativeDatesAndPricesAndDoNotFlattenMultipleSales() throws Exception {
  db.update("INSERT INTO penny_catalog_products(id,code,name,category,origin) VALUES(1,'HW','Computer','HARDWARE','THIRD_PARTY')");
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"catalogProductId\":1,\"business\":{\"warrantyYears\":1}").replace("SOFTWARE","HARDWARE"))).andExpect(status().isOk());
  db.update("INSERT INTO penny_sales(id,customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price) VALUES(1,1,1,'HW','Computer','THIRD_PARTY','HARDWARE','2024-02-29',2,123.45),(2,1,1,'HW','Computer','THIRD_PARTY','HARDWARE','2025-03-01',1,150.50)");
  db.update("INSERT INTO penny_sale_allocations(sale_id,installed_product_id,customer_id,catalog_product_id,quantity) VALUES(1,1,1,1,1)");
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.effectiveSoldOn").value("2024-02-29")).andExpect(jsonPath("$.effectivePrice").value("123.45")).andExpect(jsonPath("$.warrantyUntil").value("2025-02-28"));
  mvc.perform(put("/api/installed-products/1").principal(admin).contentType(APPLICATION_JSON).content(input(",\"catalogProductId\":1,\"business\":{\"saleSource\":\"OUR_SALE\",\"salePrice\":\"999.00\"}").replace("SOFTWARE","HARDWARE"))).andExpect(status().isBadRequest());
  db.update("INSERT INTO penny_sale_allocations(sale_id,installed_product_id,customer_id,catalog_product_id,quantity) VALUES(2,1,1,1,1)");
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.saleLinks.length()").value(2)).andExpect(jsonPath("$.effectiveSoldOn").isEmpty()).andExpect(jsonPath("$.effectivePrice").isEmpty()).andExpect(jsonPath("$.warrantyUntil").isEmpty());
 }

 @Test void serviceAllowsOtherTaxonomyButRejectsContractAndTrainingTypes() throws Exception {
  // Reset only this fixture after parent setup truncates the referencing products.
  db.update("DELETE FROM penny_product_types WHERE category_id IN (SELECT id FROM penny_product_categories WHERE code='SERVICE_TEST')");
  db.update("DELETE FROM penny_product_categories WHERE code='SERVICE_TEST'");
  Long category=db.queryForObject("INSERT INTO penny_product_categories(code,name,operational_kind,legacy_category) VALUES('SERVICE_TEST','Servizi test','OTHER','SERVICE') RETURNING id",Long.class);
  Long other=db.queryForObject("INSERT INTO penny_product_types(category_id,name) VALUES(?,'Altro servizio') RETURNING id",Long.class,category);
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"productTypeId\":"+other).replace("SOFTWARE","SERVICE"))).andExpect(status().isOk());
  for(String kind:new String[]{"CONTRACT","TRAINING"}){
   Long type=db.queryForObject("SELECT t.id FROM penny_product_types t JOIN penny_product_categories c ON c.id=t.category_id WHERE c.operational_kind=? ORDER BY t.id LIMIT 1",Long.class,kind);
   mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"productTypeId\":"+type).replace("SOFTWARE","SERVICE"))).andExpect(status().isBadRequest());
  }
 }

 @Test void technicalUsersCannotReadOrChangeMoneyButCanEditReleasesWithoutLosingIt() throws Exception {
  db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES('tech-money@test.invalid','Tech','unused',false)");
  db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email='tech-money@test.invalid' AND r.code='TECH'");
  var tech=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("tech-money@test.invalid","unused");
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"business\":{\"saleSource\":\"OUR_SALE\",\"salePrice\":\"123.45\"}"))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(tech)).andExpect(status().isOk()).andExpect(jsonPath("$.financialRead").value(false)).andExpect(jsonPath("$.business.salePrice").doesNotExist()).andExpect(jsonPath("$.effectivePrice").isEmpty());
  mvc.perform(put("/api/installed-products/1").principal(tech).contentType(APPLICATION_JSON).content(input(",\"business\":{\"usageRelease\":\"2027\"}"))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.business.salePrice").value("123.45")).andExpect(jsonPath("$.business.usageRelease").value("2027"));
  mvc.perform(put("/api/installed-products/1").principal(tech).contentType(APPLICATION_JSON).content(input(",\"business\":{\"salePrice\":\"999.00\"}"))).andExpect(status().isForbidden());
  mvc.perform(put("/api/installed-products/1").principal(tech).contentType(APPLICATION_JSON).content(input(",\"business\":null"))).andExpect(status().isForbidden());
 }


 @Test void catalogWarrantyDefaultsToInstalledHardwareAndCalculatesExpiry() throws Exception {
  db.update("INSERT INTO penny_catalog_products(id,code,name,category,origin,warranty_years) VALUES(1,'HW-WARRANTY','Computer','HARDWARE','THIRD_PARTY',2)");
  mvc.perform(post("/api/installed-products").principal(admin).contentType(APPLICATION_JSON).content(input(",\"catalogProductId\":1,\"business\":{\"saleSource\":\"OUR_SALE\",\"soldOn\":\"2024-02-29\"}").replace("SOFTWARE","HARDWARE"))).andExpect(status().isOk());
  mvc.perform(get("/api/installed-products/1").principal(admin)).andExpect(jsonPath("$.business.warrantyYears").value(2)).andExpect(jsonPath("$.warrantyUntil").value("2026-02-28"));
 }
}
