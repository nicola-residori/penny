package it.penny.api.products;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
class ProductTaxonomyIntegrationTest extends ProductCatalogIntegrationTest {
 @Test void createsCustomCategoryTypeAndPreservesLegacyFields() throws Exception {
  mvc.perform(get("/api/product-categories").principal(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.code=='CONTRACT')].name").value("Contratto"));
  long category=new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(post("/api/product-categories").principal(admin).contentType(APPLICATION_JSON).content("{\"name\":\"Ricambi\",\"operationalKind\":\"HARDWARE\",\"active\":true}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
  long type=new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(post("/api/product-types").principal(admin).contentType(APPLICATION_JSON).content("{\"categoryId\":"+category+",\"name\":\"Kit ricambio\",\"obsolete\":false}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
  String body="{\"code\":\"RIC\",\"name\":\"Kit\",\"origin\":\"THIRD_PARTY\",\"categoryId\":"+category+",\"productTypeId\":"+type+",\"unit\":\"pz\",\"supplyChannel\":\"INTERNAL_SALE\"}";
  long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(post("/api/catalog-products").principal(admin).contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(get("/api/catalog-products/"+id).principal(admin)).andExpect(jsonPath("$.category").value("HARDWARE")).andExpect(jsonPath("$.categoryName").value("Ricambi")).andExpect(jsonPath("$.productTypeName").value("Kit ricambio")).andExpect(jsonPath("$.unit").value("pz"));
  mvc.perform(put("/api/product-types/"+type).principal(admin).contentType(APPLICATION_JSON).content("{\"categoryId\":"+category+",\"name\":\"Kit ricambio\",\"obsolete\":true}")).andExpect(status().isOk());
  mvc.perform(post("/api/sales").principal(admin).contentType(APPLICATION_JSON).content("{\"customerId\":1,\"catalogProductId\":"+id+",\"soldOn\":\"2026-10-26\",\"quantity\":1,\"unitPrice\":10}")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/catalog-products/"+id).principal(admin)).andExpect(jsonPath("$.productTypeObsolete").value(true));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO penny_sales(customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price) VALUES (1,?,'RIC','Kit','THIRD_PARTY','HARDWARE',current_date,1,10)",id));
 }
 @Test void imageRequiresCatalogPermissionsAndValidBitmap() throws Exception {
  long id=entry("IMG","INTERNAL");byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aNBkAAAAASUVORK5CYII=");
  mvc.perform(multipart("/api/catalog-products/"+id+"/image").file(new MockMultipartFile("file","p.png","image/png",png)).principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/catalog-products/"+id+"/image").principal(tech)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(png));
  mvc.perform(multipart("/api/catalog-products/"+id+"/image").file(new MockMultipartFile("file","bad.png","image/png","<script>x</script>".getBytes())).principal(admin)).andExpect(status().isBadRequest());
  mvc.perform(multipart("/api/catalog-products/"+id+"/image").file(new MockMultipartFile("file","p.png","image/png",png)).principal(tech)).andExpect(status().isForbidden());
 }
}
