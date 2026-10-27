package it.penny.api.sales;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {
 private final M2Support support;
 public OverviewController(M2Support support){this.support=support;}
 @GetMapping public Map<String,Object> overview(Authentication auth){
  var user=support.user(auth);if(user==null||!user.enabled||user.mustChangePassword)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Accesso non consentito");
  var result=new LinkedHashMap<String,Object>();
  result.put("customers",support.allowed(user,"CUSTOMERS_READ")?support.db.queryForObject("SELECT count(*) FROM penny_customers WHERE deleted_at IS NULL AND NOT archived",Long.class):null);
  result.put("catalog",support.allowed(user,"CATALOG_READ")?support.db.queryForObject("SELECT count(*) FROM penny_catalog_products WHERE deleted_at IS NULL AND active",Long.class):null);
  result.put("installed",support.allowed(user,"PRODUCTS_READ")?support.db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE deleted_at IS NULL AND status<>'RETIRED'",Long.class):null);
  result.put("workstations",support.allowed(user,"WORKSTATIONS_READ")?support.db.queryForObject("SELECT count(*) FROM penny_workstations WHERE deleted_at IS NULL AND active",Long.class):null);
  var p=SalesQueries.params("",null,null,null,0,20);var totals=new SalesQueries(support).totals(SalesQueries.WHERE,p,support.allowed(user,"SALES_READ"));
  for(String field:List.of("soldQuantity","allocatedQuantity","availableQuantity"))result.put(field,support.allowed(user,"INVENTORY_READ")?totals.get(field):null);
  result.put("salesAmount",support.allowed(user,"SALES_READ")?totals.get("totalAmount"):null);return result;
 }
}
