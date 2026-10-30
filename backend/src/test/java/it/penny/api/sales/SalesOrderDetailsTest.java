package it.penny.api.sales;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class SalesOrderDetailsTest {
 @Test void unknownActivationDoesNotInventExpiryOrRequireLicenseId(){var b=JsonNodeFactory.instance.objectNode().put("licenseType","ANNUAL").put("durationMonths",12);var result=SalesOrderController.validateBusiness(b);assertFalse(result.has("expiresOn"));assertFalse(result.has("licenseId"));assertFalse(b.has("expiresOn"));}
 @Test void durationsUseEffectiveStartAndPreserveExplicitExpiry(){var b=JsonNodeFactory.instance.objectNode().put("effectiveFrom","2026-11-15").put("durationMonths",12).put("warrantyMonths",24);var result=SalesOrderController.validateBusiness(b);assertEquals("2027-11-15",result.path("expiresOn").asText());assertEquals("2028-11-15",result.path("warrantyExpiresOn").asText());b.put("expiresOn","2027-12-31");assertEquals("2027-12-31",SalesOrderController.validateBusiness(b).path("expiresOn").asText());}
 @Test void rejectsInvalidDurationsAndInvertedDates(){assertThrows(ResponseStatusException.class,()->SalesOrderController.validateBusiness(JsonNodeFactory.instance.objectNode().put("durationMonths",-1)));assertThrows(ResponseStatusException.class,()->SalesOrderController.validateBusiness(JsonNodeFactory.instance.objectNode().put("effectiveFrom","2026-11-15").put("expiresOn","2026-11-14")));assertThrows(ResponseStatusException.class,()->SalesOrderController.validateBusiness(JsonNodeFactory.instance.objectNode().put("effectiveFrom","invalid")));}
}
