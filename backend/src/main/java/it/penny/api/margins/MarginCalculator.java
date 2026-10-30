package it.penny.api.margins;
import java.math.*;
import java.util.*;
public final class MarginCalculator {
 private MarginCalculator(){}
 public static BigDecimal calculate(BigDecimal base,String calculation,BigDecimal value,int sign){return (calculation.equals("PERCENT")?base.multiply(value).divide(new BigDecimal("100")):value).multiply(BigDecimal.valueOf(sign)).setScale(2,RoundingMode.HALF_UP);}
 public static boolean matches(Map<String,Object> conditions,Map<String,Object> context){return conditions.entrySet().stream().allMatch(e->{Object actual=context.get(e.getKey());return actual!=null&&(e.getValue() instanceof List<?> choices?choices.stream().anyMatch(v->v.toString().equals(actual.toString())):e.getValue().toString().equals(actual.toString()));});}
}
