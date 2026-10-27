package it.penny.api.products;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class CatalogCommercialFieldsTest {
 private final ObjectMapper json=new ObjectMapper();
 @Test void priceIsOptionalExactAndBounded() throws Exception {
  assertNull(CatalogProductController.price(null));
  assertNull(CatalogProductController.price(json.readTree("null")));
  assertEquals(new BigDecimal("0"),CatalogProductController.price(json.readTree("0")));
  assertEquals(new BigDecimal("999999999.99"),CatalogProductController.price(json.readTree("\"999999999.99\"")));
  for(String value:new String[]{"-0.01","1000000000","0.001","\"invalid\"","true","{}"})assertThrows(org.springframework.web.server.ResponseStatusException.class,()->CatalogProductController.price(json.readTree(value)));
 }
 @Test void trainingIsCatalogOnly(){assertDoesNotThrow(()->CatalogProductController.catalogCategory("TRAINING"));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->CatalogProductController.category("TRAINING"));}
}
