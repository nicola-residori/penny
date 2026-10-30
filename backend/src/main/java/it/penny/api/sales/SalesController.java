package it.penny.api.sales;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/sales")
public class SalesController {
 private final it.penny.api.margins.MarginService margins; private final M2Support support;private final SalesQueries query;private final it.penny.api.records.CustomerRecordService records;
 public SalesController(M2Support support,it.penny.api.records.CustomerRecordService records,it.penny.api.margins.MarginService margins){this.margins=margins;this.support=support;this.records=records;query=new SalesQueries(support);}
 public record Input(Long customerId,Long catalogProductId,LocalDate soldOn,Integer quantity,BigDecimal unitPrice,String reference,String notes,Boolean registerResource){}
 public record Cancel(String reason){}
 private static final String PRICE_FIELDS=",s.unit_price AS \"unitPrice\",(s.quantity*s.unit_price)::numeric(20,2) AS \"totalAmount\",s.reference,s.notes,s.status,s.cancellation_reason AS \"cancellationReason\",s.created_at AS \"createdAt\"";
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long customerId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
  support.require(auth,"SALES_READ");var p=SalesQueries.params(q,customerId,from,to,page,size);
  if(status!=null&&!Set.of("DRAFT","CONFIRMED","CANCELLED").contains(status))throw M2Support.bad("Stato vendita non valido");
  p.addValue("status",status);String where=SalesQueries.WHERE+" AND (CAST(:status AS varchar) IS NULL OR s.status=:status)";
  return Map.of("items",M2Support.money(query.named.queryForList("SELECT "+SalesQueries.FIELDS+PRICE_FIELDS+SalesQueries.FROM+where+" ORDER BY s.sold_on DESC,s.id DESC LIMIT :size OFFSET :offset",p)),"total",Objects.requireNonNull(query.named.queryForObject("SELECT count(*)"+SalesQueries.FROM+where,p,Long.class)),"page",page,"size",size);
 }
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable long id,Authentication auth){support.require(auth,"SALES_READ");var rows=support.db.queryForList("SELECT "+SalesQueries.FIELDS+PRICE_FIELDS+SalesQueries.FROM+" WHERE s.id=?",id);if(rows.isEmpty())throw M2Support.missing("Vendita");return M2Support.money(rows.getFirst());}
 @PostMapping @Transactional public Map<String,Object> create(@RequestBody Input input,Authentication auth){
  support.require(auth,"SALES_WRITE","CUSTOMERS_READ","CATALOG_READ");if(input==null)throw M2Support.bad("Dati obbligatori");support.customer(input.customerId(),true);
  if(input.catalogProductId()==null||input.soldOn()==null)throw M2Support.bad("Prodotto e data vendita obbligatori");int qty=M2Support.quantity(input.quantity());
  BigDecimal price=input.unitPrice()==null?BigDecimal.ZERO:input.unitPrice();if(price.signum()<0||price.stripTrailingZeros().scale()>2||price.compareTo(new BigDecimal("999999999.99"))>0)throw M2Support.bad("Prezzo non valido: massimo due decimali e 999999999,99 EUR");
  var products=support.db.queryForList("SELECT * FROM penny_catalog_products WHERE id=? AND deleted_at IS NULL FOR UPDATE",input.catalogProductId());if(products.isEmpty()||!Boolean.TRUE.equals(products.getFirst().get("active")))throw M2Support.bad("Prodotto di catalogo inesistente o archiviato");
  var product=products.getFirst();
  if(!Boolean.TRUE.equals(support.db.queryForObject("SELECT c.active AND NOT coalesce(t.obsolete,false) FROM penny_catalog_products p JOIN penny_product_categories c ON c.id=p.category_id LEFT JOIN penny_product_types t ON t.id=p.product_type_id WHERE p.id=?",Boolean.class,input.catalogProductId())))throw M2Support.bad("Categoria o tipologia obsoleta: non vendibile");
  support.db.queryForObject("SELECT set_config('penny.actor',?,true)",String.class,auth.getName());
  Long id=support.db.queryForObject("INSERT INTO penny_sales(customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price,reference,notes) VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,input.customerId(),input.catalogProductId(),product.get("code"),product.get("name"),product.get("origin"),product.get("category"),input.soldOn(),qty,price,M2Support.clean(input.reference(),200),M2Support.clean(input.notes(),10000));
  Long order=support.db.queryForObject("INSERT INTO penny_sales_orders(customer_id,order_number,sold_on,status,notes) VALUES (?,?,?,'CONFIRMED',?) RETURNING id",Long.class,input.customerId(),input.reference()==null?"LEGACY-"+id:input.reference(),input.soldOn(),input.notes());
  support.db.update("UPDATE penny_sales SET order_id=? WHERE id=?",order,id);margins.snapshot(id,auth);
  support.audit(auth,"SALE_CREATED","SALE",Objects.requireNonNull(id),Map.of("quantity",qty));
  if(Boolean.TRUE.equals(input.registerResource())){
   String kind=support.db.queryForObject("SELECT operational_kind FROM penny_product_categories WHERE id=?",String.class,product.get("category_id"));long resource;
   if(Set.of("HARDWARE","SOFTWARE").contains(kind)){
    support.require(auth,"PRODUCTS_READ","PRODUCTS_WRITE");
    String business=kind.equals("SOFTWARE")?"{\"saleSource\":\"OUR_SALE\",\"softwareStatus\":\"TO_INSTALL\"}":"{\"saleSource\":\"OUR_SALE\"}";
    resource=support.db.queryForObject("INSERT INTO penny_installed_products(customer_id,category,name,origin,catalog_product_id,quantity,product_type_id,business) VALUES (?,?,?,?,?,?,?,?::jsonb) RETURNING id",Long.class,input.customerId(),product.get("category"),product.get("name"),product.get("origin"),product.get("id"),qty,product.get("product_type_id"),business);
    support.db.update("INSERT INTO penny_sale_allocations(sale_id,installed_product_id,customer_id,catalog_product_id,quantity) VALUES (?,?,?,?,?)",id,resource,input.customerId(),input.catalogProductId(),qty);
    support.audit(auth,"INSTALLATION_CREATED_FROM_SALE","INSTALLATION",resource,Map.of("saleId",id));
   }else if(Set.of("TRAINING","CONTRACT").contains(kind)){
    var details=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();details.put("soldOn",input.soldOn().toString());details.put("price",price.toPlainString());if(kind.equals("CONTRACT"))details.put("contractType","GENERAL");
    resource=((Number)records.create(new it.penny.api.records.CustomerRecordInput(input.customerId(),null,kind,(String)product.get("name"),(Long)product.get("product_type_id"),id,null,null,kind.equals("CONTRACT")?"ACTIVE":"PLANNED",input.notes(),details),auth).get("id")).longValue();
   }else throw M2Support.bad("Per questa categoria registra il dettaglio dalla scheda cliente");
   return Map.of("id",id,"resourceId",resource);
  }
  return Map.of("id",id);
 }
 @PostMapping("/{id}/cancel") @Transactional public Map<String,Object> cancel(@PathVariable long id,@RequestBody Cancel input,Authentication auth){
  support.require(auth,"SALES_WRITE");String reason=M2Support.required(input==null?null:input.reason(),1000,"Motivazione");var sale=support.row("penny_sales",id,true);
  if(!"CONFIRMED".equals(sale.get("status")))throw M2Support.conflict("Vendita già annullata");
  if(support.db.queryForObject("SELECT count(*) FROM penny_sale_allocations WHERE sale_id=?",Integer.class,id)>0)throw M2Support.conflict("Libera prima tutte le allocazioni della vendita");
  support.db.update("UPDATE penny_sales SET status='CANCELLED',cancellation_reason=?,cancelled_at=now(),updated_at=now() WHERE id=?",reason,id);
  margins.cancelSale(id,reason,auth);
  support.db.update("UPDATE penny_sales_orders SET status='CANCELLED',updated_at=now() WHERE id=? AND NOT EXISTS(SELECT 1 FROM penny_sales WHERE order_id=? AND status<>'CANCELLED')",sale.get("order_id"),sale.get("order_id"));
  support.audit(auth,"SALE_CANCELLED","SALE",id,Map.of("reason",reason));return Map.of("cancelled",true);
 }
 @GetMapping("/summary") public Map<String,Object> summary(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long customerId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
  support.require(auth,"SALES_READ");var p=SalesQueries.params(q,customerId,from,to,page,size);String where=SalesQueries.WHERE+" AND s.status='CONFIRMED'";
  String groups=" GROUP BY s.customer_id,c.name,s.catalog_product_id";
  String fields="s.customer_id AS \"customerId\",c.name AS \"customerName\",s.catalog_product_id AS \"catalogProductId\",max(s.product_code) AS code,max(s.product_name) AS \"productName\",sum(s.quantity) AS \"soldQuantity\",sum(coalesce(a.allocated,0)) AS \"allocatedQuantity\",sum(s.quantity-coalesce(a.allocated,0)) AS \"availableQuantity\",sum(s.quantity*s.unit_price)::numeric(20,2) AS \"totalAmount\"";
  var items=M2Support.money(query.named.queryForList("SELECT "+fields+SalesQueries.FROM+where+groups+" ORDER BY c.name,s.catalog_product_id LIMIT :size OFFSET :offset",p));
  Long total=query.named.queryForObject("SELECT count(*) FROM (SELECT s.customer_id"+SalesQueries.FROM+where+groups+") grouped",p,Long.class);
  return Map.of("items",items,"total",Objects.requireNonNull(total),"page",page,"size",size,"totals",query.totals(SalesQueries.WHERE,p,true));
 }
}
