package it.penny.api.sales;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.namedparam.*;
import java.util.*;

@RestController
@RequestMapping("/api/allocations")
public class AllocationsController {
 private final M2Support support;
 public AllocationsController(M2Support support){this.support=support;}
 public record Input(Long saleId,Long installedProductId,Integer quantity){}
 public record Quantity(Integer quantity){}
 private void write(Authentication auth){support.require(auth,"ALLOCATIONS_WRITE","INVENTORY_READ","PRODUCTS_READ","PRODUCTS_WRITE");}
 @GetMapping public Map<String,Object> list(@RequestParam(required=false) Long saleId,@RequestParam(required=false) Long installedProductId,@RequestParam(required=false) Long customerId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
  support.require(auth,"ALLOCATIONS_READ","INVENTORY_READ","PRODUCTS_READ");M2Support.pagination(page,size);
  var named=new NamedParameterJdbcTemplate(support.db);var p=new MapSqlParameterSource().addValue("sale",saleId).addValue("installed",installedProductId).addValue("customer",customerId).addValue("size",size).addValue("offset",page*size);
  String where=" WHERE (CAST(:sale AS bigint) IS NULL OR a.sale_id=:sale) AND (CAST(:installed AS bigint) IS NULL OR a.installed_product_id=:installed) AND (CAST(:customer AS bigint) IS NULL OR a.customer_id=:customer)";
  boolean stations=support.allowed(support.user(auth),"WORKSTATIONS_READ");
  String fields="a.id,a.sale_id AS \"saleId\",a.installed_product_id AS \"installedProductId\",a.customer_id AS \"customerId\",a.catalog_product_id AS \"catalogProductId\",a.quantity,i.name AS \"installedName\","+(stations?"w.name":"CAST(NULL AS varchar)")+" AS \"workstationName\",s.product_code AS code,s.product_name AS \"productName\",s.sold_on AS \"soldOn\"";
  String from=" FROM penny_sale_allocations a JOIN penny_sales s ON s.id=a.sale_id JOIN penny_installed_products i ON i.id=a.installed_product_id LEFT JOIN penny_workstations w ON w.id=i.workstation_id";
  var items=named.queryForList("SELECT "+fields+from+where+" ORDER BY a.id DESC LIMIT :size OFFSET :offset",p);
  return Map.of("items",items,"total",Objects.requireNonNull(named.queryForObject("SELECT count(*) FROM penny_sale_allocations a"+where,p,Long.class)),"page",page,"size",size);
 }
 private void lockCatalog(Map<String,Object> sale){support.db.queryForList("SELECT id FROM penny_catalog_products WHERE id=? FOR SHARE",sale.get("catalog_product_id"));}
 @PostMapping @Transactional public Map<String,Object> create(@RequestBody Input input,Authentication auth){
  write(auth);if(input==null||input.saleId()==null||input.installedProductId()==null)throw M2Support.bad("Vendita e installazione obbligatorie");int qty=M2Support.quantity(input.quantity());
  var sale=support.row("penny_sales",input.saleId(),false);lockCatalog(sale);sale=support.row("penny_sales",input.saleId(),true);var installed=support.row("penny_installed_products",input.installedProductId(),false);
  if(!Objects.equals(sale.get("customer_id"),installed.get("customer_id"))||!Objects.equals(sale.get("catalog_product_id"),installed.get("catalog_product_id")))throw M2Support.conflict("Vendita e installazione devono avere lo stesso cliente e prodotto di catalogo");
  Long id=support.db.queryForObject("INSERT INTO penny_sale_allocations(sale_id,installed_product_id,customer_id,catalog_product_id,quantity) VALUES (?,?,?,?,?) RETURNING id",Long.class,input.saleId(),input.installedProductId(),sale.get("customer_id"),sale.get("catalog_product_id"),qty);
  support.audit(auth,"ALLOCATION_CREATED","ALLOCATION",Objects.requireNonNull(id),Map.of("saleId",input.saleId(),"installedProductId",input.installedProductId(),"quantity",qty));return Map.of("id",id);
 }
 @PutMapping("/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,@RequestBody Quantity input,Authentication auth){
  write(auth);int qty=M2Support.quantity(input==null?null:input.quantity());var allocation=support.row("penny_sale_allocations",id,false);
  // Sale first, then allocation: same ordering as create, cancellation and database trigger.
  var sale=support.row("penny_sales",((Number)allocation.get("sale_id")).longValue(),false);lockCatalog(sale);support.row("penny_sales",((Number)allocation.get("sale_id")).longValue(),true);
  if(support.db.update("UPDATE penny_sale_allocations SET quantity=?,updated_at=now() WHERE id=?",qty,id)==0)throw M2Support.missing("Allocazione");
  support.audit(auth,"ALLOCATION_UPDATED","ALLOCATION",id,Map.of("quantity",qty));return Map.of("updated",true);
 }
 @DeleteMapping("/{id}") @Transactional public Map<String,Object> release(@PathVariable long id,Authentication auth){
  write(auth);var allocation=support.row("penny_sale_allocations",id,false);support.row("penny_sales",((Number)allocation.get("sale_id")).longValue(),true);
  if(support.db.update("DELETE FROM penny_sale_allocations WHERE id=?",id)==0)throw M2Support.missing("Allocazione");
  support.audit(auth,"ALLOCATION_RELEASED","ALLOCATION",id,Map.of("saleId",allocation.get("sale_id"),"installedProductId",allocation.get("installed_product_id"),"quantity",allocation.get("quantity")));return Map.of("released",true);
 }
}
