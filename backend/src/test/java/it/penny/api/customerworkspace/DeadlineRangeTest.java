package it.penny.api.customerworkspace;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class DeadlineRangeTest {
 @Test void acceptsInclusive366DaysAndRejectsWiderOrReversedRanges(){
  var start=LocalDate.of(2026,1,1);
  assertDoesNotThrow(()->CustomerWorkspaceController.dateRange(start,start.plusDays(365)));
  assertThrows(ResponseStatusException.class,()->CustomerWorkspaceController.dateRange(start,start.plusDays(366)));
  assertThrows(ResponseStatusException.class,()->CustomerWorkspaceController.dateRange(start,start.minusDays(1)));
 }
}
