package it.penny.api.sales;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/** Date-only calendar buckets anchored independently of the requested range. */
record AnalyticsSpan(int count, AnalyticsController.GroupBy groupBy) {
 private static final Pattern FORMAT=Pattern.compile("([1-9][0-9]{0,3})(d|w|mon|y)");
 private static final LocalDate EPOCH=LocalDate.of(1970,1,1),MONDAY=LocalDate.of(1970,1,5);
 static AnalyticsSpan parse(String value){
  var match=FORMAT.matcher(value);
  if(!match.matches())throw M2Support.bad("Span non valido: usare d, w, mon o y");
  int count=Integer.parseInt(match.group(1));
  var group=switch(match.group(2)){case "d"->AnalyticsController.GroupBy.DAY;case "w"->AnalyticsController.GroupBy.WEEK;case "mon"->AnalyticsController.GroupBy.MONTH;default->AnalyticsController.GroupBy.YEAR;};
  if((group==AnalyticsController.GroupBy.YEAR&&count>200)||(group==AnalyticsController.GroupBy.MONTH&&count>2400))throw M2Support.bad("Span troppo ampio");
  return new AnalyticsSpan(count,group);
 }
 static AnalyticsSpan legacy(String value){
  return switch(value){case "MONTH"->new AnalyticsSpan(1,AnalyticsController.GroupBy.MONTH);case "YEAR"->new AnalyticsSpan(1,AnalyticsController.GroupBy.YEAR);default->throw M2Support.bad("Raggruppamento non valido");};
 }
 long index(LocalDate date){
  long distance=switch(groupBy){case DAY->ChronoUnit.DAYS.between(EPOCH,date);case WEEK->ChronoUnit.DAYS.between(MONDAY,date);case MONTH->(date.getYear()-1970L)*12+date.getMonthValue()-1;case YEAR->date.getYear()-1970L;};
  return Math.floorDiv(distance,groupBy==AnalyticsController.GroupBy.WEEK?count*7L:count);
 }
 LocalDate start(LocalDate date){
  long offset=index(date)*count;
  return switch(groupBy){case DAY->EPOCH.plusDays(offset);case WEEK->MONDAY.plusWeeks(offset);case MONTH->EPOCH.plusMonths(offset);case YEAR->EPOCH.plusYears(offset);};
 }
 LocalDate next(LocalDate start){return switch(groupBy){case DAY->start.plusDays(count);case WEEK->start.plusWeeks(count);case MONTH->start.plusMonths(count);case YEAR->start.plusYears(count);};}
 String canonical(){return count+switch(groupBy){case DAY->"d";case WEEK->"w";case MONTH->"mon";case YEAR->"y";};}
}
