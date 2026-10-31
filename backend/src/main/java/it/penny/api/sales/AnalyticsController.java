package it.penny.api.sales;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Commercial history is aggregated independently of allocations and current record visibility. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
 private final M2Support support;
 public AnalyticsController(M2Support support){this.support=support;}
 public enum GroupBy { DAY,WEEK,MONTH,YEAR }
 public record Permissions(boolean salesRead,boolean customersRead) {}
 public record Metrics(String salesAmount,Long soldQuantity,Long saleCount,Long newCustomers,Long customersToDate,Long unknownCustomerDates) {}
 public record Percent(String salesAmount,String soldQuantity,String customersToDate) {}
 public record Comparison(LocalDate from,LocalDate to,Metrics previous,Metrics gap,Percent percent) {}
 public record Period(LocalDate period,LocalDate periodEnd,String salesAmount,Long soldQuantity,Long saleCount,Long newCustomers,Long customersToDate,Comparison comparison) {}
 public record Analytics(LocalDate from,LocalDate to,GroupBy groupBy,String span,Long customerId,Permissions permissions,Metrics totals,List<Period> series) {}
 private record Sales(BigDecimal amount,long quantity,long count) {
  static final Sales ZERO=new Sales(new BigDecimal("0.00"),0,0);
  Sales plus(Sales other){return new Sales(amount.add(other.amount),quantity+other.quantity,count+other.count);}
 }
 @GetMapping
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public Analytics analytics(@RequestParam LocalDate from,@RequestParam LocalDate to,
   @RequestParam(defaultValue="YEAR") String groupBy,@RequestParam(required=false) String span,@RequestParam(required=false) Long customerId,@RequestParam(defaultValue="false") boolean comparePreviousYear,Authentication auth){
  var user=support.user(auth);
  boolean salesRead=support.allowed(user,"SALES_READ"),customersRead=support.allowed(user,"CUSTOMERS_READ");
  if(!salesRead&&!customersRead)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Accesso non consentito");
  if(from.isBefore(LocalDate.of(1900,1,1))||to.isAfter(LocalDate.of(2100,12,31))||to.isBefore(from))throw M2Support.bad("Intervallo di date non valido");
  AnalyticsSpan bucket=span==null?AnalyticsSpan.legacy(groupBy):AnalyticsSpan.parse(span);
  LocalDate first=bucket.start(from),last=bucket.start(to);
  long buckets=bucket.index(to)-bucket.index(from)+1;
  long limit=span!=null?500:bucket.groupBy()==GroupBy.MONTH?120:201;
  if(buckets>limit)throw M2Support.bad("Troppi periodi richiesti");
  if(customerId!=null){
   if(customerId<1)throw M2Support.bad("Cliente non valido");
   if(!customersRead)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Accesso clienti non consentito");
   if(support.db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=? AND deleted_at IS NULL",Long.class,customerId)==0)throw M2Support.missing("Cliente");
  }
  var sales=new HashMap<LocalDate,Sales>();
  if(salesRead){
   var args=new ArrayList<Object>(List.of(from,to));if(customerId!=null)args.add(customerId);
   support.db.query("SELECT sold_on AS period,sum(quantity*unit_price) AS amount,sum(quantity) AS quantity,count(*) AS count FROM penny_sales WHERE status='CONFIRMED' AND sold_on>=? AND sold_on<=?"+(customerId==null?"":" AND customer_id=?")+" GROUP BY 1",rs->{
    sales.merge(bucket.start(rs.getDate("period").toLocalDate()),new Sales(rs.getBigDecimal("amount"),rs.getLong("quantity"),rs.getLong("count")),Sales::plus);
   },args.toArray());
  }
  var acquisitions=new HashMap<LocalDate,Long>();
  long cumulative=0,unknown=0;
  if(customersRead){
   // First dates deliberately include cancelled sales: cancellation never rewrites acquisition history.
   var args=new ArrayList<Object>();if(customerId!=null)args.add(customerId);args.add(from);args.add(to);
   var rows=support.db.queryForList("WITH first_sales AS (SELECT c.id,min(s.sold_on) AS first_date FROM penny_customers c LEFT JOIN penny_sales s ON s.customer_id=c.id WHERE c.lifecycle_status='CUSTOMER'"+(customerId==null?"":" AND c.id=?")+" GROUP BY c.id) SELECT CASE WHEN first_date<? THEN DATE '0001-01-01' ELSE first_date END AS period,count(*) AS count FROM first_sales WHERE first_date<=? OR first_date IS NULL GROUP BY 1",args.toArray());
   for(var row:rows){
    long count=((Number)row.get("count")).longValue();
    if(row.get("period")==null)unknown+=count;
    else {LocalDate date=((java.sql.Date)row.get("period")).toLocalDate();if(date.getYear()==1)cumulative+=count;else acquisitions.merge(bucket.start(date),count,Long::sum);}
   }
  }
  var series=new ArrayList<Period>();Sales total=Sales.ZERO;long newTotal=0;
  for(LocalDate date=first;!date.isAfter(last);date=bucket.next(date)){
   Sales value=sales.getOrDefault(date,Sales.ZERO);total=total.plus(value);
   long acquired=acquisitions.getOrDefault(date,0L);newTotal+=acquired;cumulative+=acquired;
   series.add(new Period(date,bucket.next(date).minusDays(1),salesRead?value.amount.toPlainString():null,salesRead?value.quantity:null,salesRead?value.count:null,customersRead?acquired:null,customersRead?cumulative:null,null));
  }
  if(comparePreviousYear)series=new AnalyticsYearComparison(support).attach(series,from,to,bucket,customerId,salesRead,customersRead);
  return new Analytics(from,to,bucket.groupBy(),bucket.canonical(),customerId,new Permissions(salesRead,customersRead),new Metrics(salesRead?total.amount.toPlainString():null,salesRead?total.quantity:null,salesRead?total.count:null,customersRead?newTotal:null,customersRead?cumulative:null,customersRead?unknown:null),series);
 }
}
