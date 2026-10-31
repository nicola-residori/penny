package it.penny.api.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** A previous-year window is aligned to each visible interval, never re-bucketed on an epoch. */
final class AnalyticsYearComparison {
 private final M2Support support;
 AnalyticsYearComparison(M2Support support){this.support=support;}
 private record Sales(BigDecimal amount,long quantity,long count){}
 private record Window(LocalDate from,LocalDate to){}
 private static Window previousWindow(LocalDate from,LocalDate to,AnalyticsSpan span){
  LocalDate previousFrom=from.minusYears(1),previousTo=to.minusYears(1);
  // A standalone leap day has no equivalent; within a wider range it does not duplicate February 28.
  if(from.getMonthValue()==2&&from.getDayOfMonth()==29)previousFrom=previousFrom.plusDays(1);
  // Entire calendar months/years compare entire months, including a prior leap February.
  if((span.groupBy()==AnalyticsController.GroupBy.MONTH||span.groupBy()==AnalyticsController.GroupBy.YEAR)&&from.getDayOfMonth()==1&&to.getDayOfMonth()==to.lengthOfMonth())previousTo=to.plusDays(1).minusYears(1).minusDays(1);
  return previousFrom.isAfter(previousTo)?null:new Window(previousFrom,previousTo);
 }
 ArrayList<AnalyticsController.Period> attach(List<AnalyticsController.Period> points,LocalDate from,LocalDate to,AnalyticsSpan span,Long customer,boolean salesRead,boolean customersRead){
  var windows=new ArrayList<Window>();
  for(var p:points)windows.add(previousWindow(p.period().isBefore(from)?from:p.period(),p.periodEnd().isAfter(to)?to:p.periodEnd(),span));
  var valid=windows.stream().filter(Objects::nonNull).toList();
  if(valid.isEmpty())return new ArrayList<>(points);
  LocalDate earliest=valid.stream().map(Window::from).min(LocalDate::compareTo).orElseThrow(),latest=valid.stream().map(Window::to).max(LocalDate::compareTo).orElseThrow();
  var daily=new TreeMap<LocalDate,Sales>();var acquisitions=new TreeMap<LocalDate,Long>();
  if(salesRead){var args=new ArrayList<Object>(List.of(earliest,latest));if(customer!=null)args.add(customer);
   support.db.query("SELECT sold_on,sum(quantity*unit_price) AS amount,sum(quantity) AS quantity,count(*) AS count FROM penny_sales WHERE status='CONFIRMED' AND sold_on BETWEEN ? AND ?"+(customer==null?"":" AND customer_id=?")+" GROUP BY sold_on",rs->{daily.put(rs.getDate("sold_on").toLocalDate(),new Sales(rs.getBigDecimal("amount"),rs.getLong("quantity"),rs.getLong("count")));},args.toArray());
  }
  if(customersRead){var args=new ArrayList<Object>();if(customer!=null)args.add(customer);args.add(latest);
   support.db.query("SELECT first_date,count(*) AS count FROM (SELECT min(s.sold_on) AS first_date FROM penny_customers c LEFT JOIN penny_sales s ON s.customer_id=c.id WHERE c.lifecycle_status='CUSTOMER'"+(customer==null?"":" AND c.id=?")+" GROUP BY c.id) first_sales WHERE first_date<=? GROUP BY first_date",rs->{acquisitions.put(rs.getDate("first_date").toLocalDate(),rs.getLong("count"));},args.toArray());
  }
  var result=new ArrayList<AnalyticsController.Period>();
  for(int i=0;i<points.size();i++){
   var p=points.get(i);var window=windows.get(i);AnalyticsController.Comparison comparison=null;
   if(window!=null){BigDecimal amount=new BigDecimal("0.00");long quantity=0,count=0;
    for(var sale:daily.subMap(window.from(),true,window.to(),true).values()){amount=amount.add(sale.amount());quantity+=sale.quantity();count+=sale.count();}
    long acquired=acquisitions.subMap(window.from(),true,window.to(),true).values().stream().mapToLong(Long::longValue).sum(),cumulative=acquisitions.headMap(window.to(),true).values().stream().mapToLong(Long::longValue).sum();
    var previous=new AnalyticsController.Metrics(salesRead?amount.toPlainString():null,salesRead?quantity:null,salesRead?count:null,customersRead?acquired:null,customersRead?cumulative:null,null);
    var gap=new AnalyticsController.Metrics(subtract(p.salesAmount(),previous.salesAmount()),subtract(p.soldQuantity(),previous.soldQuantity()),subtract(p.saleCount(),previous.saleCount()),subtract(p.newCustomers(),previous.newCustomers()),subtract(p.customersToDate(),previous.customersToDate()),null);
    comparison=new AnalyticsController.Comparison(window.from(),window.to(),previous,gap,new AnalyticsController.Percent(percent(gap.salesAmount(),previous.salesAmount()),percent(gap.soldQuantity(),previous.soldQuantity()),percent(gap.customersToDate(),previous.customersToDate())));
   }
   result.add(new AnalyticsController.Period(p.period(),p.periodEnd(),p.salesAmount(),p.soldQuantity(),p.saleCount(),p.newCustomers(),p.customersToDate(),comparison));
  }
  return result;
 }
 private static String subtract(String current,String previous){return current==null||previous==null?null:new BigDecimal(current).subtract(new BigDecimal(previous)).toPlainString();}
 private static Long subtract(Long current,Long previous){return current==null||previous==null?null:current-previous;}
 private static String percent(Object gap,Object previous){if(gap==null||previous==null||new BigDecimal(previous.toString()).signum()==0)return null;return new BigDecimal(gap.toString()).multiply(new BigDecimal("100")).divide(new BigDecimal(previous.toString()).abs(),2,RoundingMode.HALF_UP).toPlainString();}
}
