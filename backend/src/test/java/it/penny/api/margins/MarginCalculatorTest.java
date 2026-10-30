package it.penny.api.margins;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MarginCalculatorTest {
 @Test void originalExampleHasSignedTotal290(){BigDecimal base=new BigDecimal("1000"),total=BigDecimal.ZERO;for(int percent:new int[]{5,15,12,-3})total=total.add(MarginCalculator.calculate(base,"PERCENT",BigDecimal.valueOf(Math.abs(percent)),percent<0?-1:1));assertEquals(new BigDecimal("290.00"),total);}
 @Test void exactMoneyRoundsOnlyFinalComponent(){assertEquals(new BigDecimal("0.01"),MarginCalculator.calculate(new BigDecimal("0.10"),"PERCENT",new BigDecimal("5"),1));assertEquals(new BigDecimal("-12.35"),MarginCalculator.calculate(new BigDecimal("999"),"FIXED",new BigDecimal("12.345"),-1));}
 @Test void allConditionsMustMatchAndMissingDataNeverQualifies(){var condition=Map.<String,Object>of("operationType",List.of("NEW_LICENSE","FIRST_RENEWAL"),"licenseType","ANNUAL");assertTrue(MarginCalculator.matches(condition,Map.of("operationType","NEW_LICENSE","licenseType","ANNUAL")));assertFalse(MarginCalculator.matches(condition,Map.of("operationType","NEW_LICENSE")));assertFalse(MarginCalculator.matches(condition,Map.of("operationType","RENEWAL","licenseType","ANNUAL")));assertTrue(MarginCalculator.matches(Map.of(),Map.of()));}
}
