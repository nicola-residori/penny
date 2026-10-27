package it.penny.api.sales;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
 private final M2Support support;private final SalesQueries query;
 public InventoryController(M2Support support){this.support=support;query=new SalesQueries(support);}
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long customerId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
  support.require(auth,"INVENTORY_READ");var p=SalesQueries.params(q,customerId,from,to,page,size);String installable=SalesQueries.WHERE+" AND s.category<>'TRAINING'";String where=installable+" AND s.status='CONFIRMED'";
  var items=query.named.queryForList("SELECT "+SalesQueries.FIELDS+SalesQueries.FROM+where+" ORDER BY s.sold_on DESC,s.id DESC LIMIT :size OFFSET :offset",p);
  Long total=query.named.queryForObject("SELECT count(*)"+SalesQueries.FROM+where,p,Long.class);
  return Map.of("items",items,"total",Objects.requireNonNull(total),"page",page,"size",size,"totals",query.totals(installable,p,false));
 }
}
