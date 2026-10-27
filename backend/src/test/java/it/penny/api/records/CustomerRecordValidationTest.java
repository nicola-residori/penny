package it.penny.api.records;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class CustomerRecordValidationTest {
 private final ObjectMapper json=new ObjectMapper();
 @Test void hourlyContractRejectsRemainingAboveTotal() throws Exception {
  assertThrows(ResponseStatusException.class,()->CustomerRecordService.validateDetails("CONTRACT",json.readTree("{\"contractType\":\"HOURLY\",\"totalHours\":\"10.5\",\"remainingHours\":\"11\"}")));
 }
 @Test void hourlyContractPreservesExactDecimal() throws Exception {
  var value=CustomerRecordService.validateDetails("CONTRACT",json.readTree("{\"contractType\":\"HOURLY\",\"totalHours\":\"10.50\",\"remainingHours\":\"9.25\",\"price\":\"123.45\"}"));
  assertEquals("123.45",value.get("price").asText());
 }
 @Test void trainingRejectsUnknownKeysAndInvalidVenue() throws Exception {
  assertThrows(ResponseStatusException.class,()->CustomerRecordService.validateDetails("TRAINING",json.readTree("{\"venue\":\"MOON\"}")));
  assertThrows(ResponseStatusException.class,()->CustomerRecordService.validateDetails("TRAINING",json.readTree("{\"unexpected\":true}")));
 }
 @Test void saleOverridesManualPriceAndDateExactly() throws Exception {
  var details=CustomerRecordService.saleDetails("TRAINING",7L,true,java.util.Map.of("customer_id",7L,"operational_kind","TRAINING","status","CONFIRMED","sold_on",java.sql.Date.valueOf("2026-10-26"),"unit_price",new java.math.BigDecimal("123.45")),(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("{\"price\":\"999\",\"soldOn\":\"2000-01-01\"}"));
  assertEquals("123.45",details.path("price").asText());assertEquals("2026-10-26",details.path("soldOn").asText());
 }
 @Test void saleRejectsForeignCustomerWrongKindAndNewCancelledLink() {
  var details=json.createObjectNode();
  assertThrows(ResponseStatusException.class,()->CustomerRecordService.saleDetails("CONTRACT",7L,true,java.util.Map.of("customer_id",8L,"operational_kind","CONTRACT","status","CONFIRMED"),details));
  assertThrows(ResponseStatusException.class,()->CustomerRecordService.saleDetails("CONTRACT",7L,true,java.util.Map.of("customer_id",7L,"operational_kind","HARDWARE","status","CONFIRMED"),details));
  assertThrows(ResponseStatusException.class,()->CustomerRecordService.saleDetails("CONTRACT",7L,true,java.util.Map.of("customer_id",7L,"operational_kind","CONTRACT","status","CANCELLED"),details));
 }
 @Test void existingCancelledSaleRemainsLinkedAndAuthoritative() {
  var result=CustomerRecordService.saleDetails("CONTRACT",7L,false,java.util.Map.of("customer_id",7L,"operational_kind","CONTRACT","status","CANCELLED","sold_on",java.sql.Date.valueOf("2026-10-26"),"unit_price",new java.math.BigDecimal("10.00")),json.createObjectNode());assertEquals("10.00",result.path("price").asText());
 }
 @Test void operationalReadRedactsManualDerivedAndCollaboratorAmounts() throws Exception {
  var details=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("{\"price\":\"123.45\",\"collaboratorCost\":\"10.00\",\"soldOn\":\"2026-10-26\"}");
  var hidden=CustomerRecordService.readFinancials(details,false);assertFalse(hidden.has("price"));assertFalse(hidden.has("collaboratorCost"));assertEquals("2026-10-26",hidden.path("soldOn").asText());assertTrue(details.has("price"));
  var derived=CustomerRecordService.saleDetails("TRAINING",7L,false,java.util.Map.of("customer_id",7L,"operational_kind","TRAINING","status","CONFIRMED","sold_on",java.sql.Date.valueOf("2026-10-26"),"unit_price",new java.math.BigDecimal("123.45")),json.createObjectNode());assertFalse(CustomerRecordService.readFinancials(derived,false).has("price"));
 }
 @Test void hiddenOmittedAmountsSurviveOperationalEditButChangesRequireSalesWrite() throws Exception {
  var previous=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("{\"price\":\"123.45\",\"collaboratorCost\":\"10.00\"}");
  var preserved=CustomerRecordService.writeFinancials(json.createObjectNode(),previous,false,false);assertEquals("123.45",preserved.path("price").asText());assertEquals("10.00",preserved.path("collaboratorCost").asText());
  var changed=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("{\"price\":\"500\"}");var denied=assertThrows(ResponseStatusException.class,()->CustomerRecordService.writeFinancials(changed,previous,false,false));assertEquals(403,denied.getStatusCode().value());
  assertDoesNotThrow(()->CustomerRecordService.writeFinancials(previous.deepCopy(),previous,false,false));
 }
}
