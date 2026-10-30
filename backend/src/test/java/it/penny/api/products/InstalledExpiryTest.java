package it.penny.api.products;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class InstalledExpiryTest {
 @Test void transitionsAtTwentyDaysAndDayAfterExpiryWhilePreservingManualStates() {
  var node=new ObjectMapper().createObjectNode();var today=LocalDate.of(2026,10,30);
  InstalledBusiness.applyExpiry(node,"SOFTWARE",today.plusDays(21),today);assertEquals("ACTIVE",node.path("softwareStatus").asText());
  InstalledBusiness.applyExpiry(node,"SOFTWARE",today.plusDays(20),today);assertEquals("EXPIRING",node.path("softwareStatus").asText());
  InstalledBusiness.applyExpiry(node,"SOFTWARE",today,today);assertEquals("EXPIRING",node.path("softwareStatus").asText());
  InstalledBusiness.applyExpiry(node,"SOFTWARE",today.minusDays(1),today);assertEquals("EXPIRED",node.path("softwareStatus").asText());
  node.put("softwareStatus","REPLACED");InstalledBusiness.applyExpiry(node,"SOFTWARE",today.minusDays(1),today);assertEquals("REPLACED",node.path("softwareStatus").asText());
 }
}
