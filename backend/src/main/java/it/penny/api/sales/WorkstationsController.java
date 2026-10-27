package it.penny.api.sales;

import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.namedparam.*;
import java.util.*;

@RestController
@RequestMapping("/api/workstations")
public class WorkstationsController {
 private final M2Support support;
 public WorkstationsController(M2Support support){this.support=support;}
 public record Input(Long customerId,String name,String location,String notes,Boolean active){}
 private static final String FIELDS="w.id,w.customer_id AS \"customerId\",c.name AS \"customerName\",w.name,w.location,w.notes,w.active,w.created_at AS \"createdAt\",w.updated_at AS \"updatedAt\"";
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long customerId,@RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
  support.require(auth,"WORKSTATIONS_READ");M2Support.pagination(page,size);String like=M2Support.like(q);var named=new NamedParameterJdbcTemplate(support.db);
  var p=new MapSqlParameterSource().addValue("q",like).addValue("customer",customerId).addValue("active",active).addValue("size",size).addValue("offset",page*size);
  String where=" WHERE w.deleted_at IS NULL AND (CAST(:customer AS bigint) IS NULL OR w.customer_id=:customer) AND (CAST(:active AS boolean) IS NULL OR w.active=:active) AND (lower(w.name) LIKE :q ESCAPE '\\' OR lower(coalesce(w.location,'')) LIKE :q ESCAPE '\\')";
  String from=" FROM penny_workstations w JOIN penny_customers c ON c.id=w.customer_id";
  var items=named.queryForList("SELECT "+FIELDS+from+where+" ORDER BY w.id DESC LIMIT :size OFFSET :offset",p);
  return Map.of("items",items,"total",Objects.requireNonNull(named.queryForObject("SELECT count(*)"+from+where,p,Long.class)),"page",page,"size",size);
 }
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable long id,Authentication auth){support.require(auth,"WORKSTATIONS_READ");var rows=support.db.queryForList("SELECT "+FIELDS+" FROM penny_workstations w JOIN penny_customers c ON c.id=w.customer_id WHERE w.id=? AND w.deleted_at IS NULL",id);if(rows.isEmpty())throw M2Support.missing("Postazione");return rows.getFirst();}
 private Object[] values(Input input,Long previousCustomer){
  if(input==null)throw M2Support.bad("Dati obbligatori");support.customer(input.customerId(),!Objects.equals(previousCustomer,input.customerId()));
  return new Object[]{input.customerId(),M2Support.required(input.name(),160,"Nome postazione"),M2Support.clean(input.location(),240),M2Support.clean(input.notes(),10000),input.active()==null||input.active()};
 }
 @PostMapping @Transactional public Map<String,Object> create(@RequestBody Input input,Authentication auth){
  support.require(auth,"WORKSTATIONS_WRITE","CUSTOMERS_READ");var values=values(input,null);
  Long id=support.db.queryForObject("INSERT INTO penny_workstations(customer_id,name,location,notes,active) VALUES (?,?,?,?,?) RETURNING id",Long.class,values);
  support.audit(auth,"WORKSTATION_CREATED","WORKSTATION",Objects.requireNonNull(id),Map.of("customerId",input.customerId()));return Map.of("id",id);
 }
 @PutMapping("/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,@RequestBody Input input,Authentication auth){
  support.require(auth,"WORKSTATIONS_WRITE");var current=support.row("penny_workstations",id,true);
  if(input!=null&&!Objects.equals(current.get("customer_id"),input.customerId()))support.require(auth,"CUSTOMERS_READ");
  if(input!=null&&!Objects.equals(current.get("customer_id"),input.customerId())&&support.db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE workstation_id=?",Integer.class,id)>0)throw M2Support.conflict("Una postazione collegata non può cambiare cliente");
  var values=values(input,(Long)current.get("customer_id"));var p=Arrays.copyOf(values,values.length+1);p[values.length]=id;
  support.db.update("UPDATE penny_workstations SET customer_id=?,name=?,location=?,notes=?,active=?,updated_at=now() WHERE id=?",p);
  support.audit(auth,"WORKSTATION_UPDATED","WORKSTATION",id,Map.of("active",input.active()==null||input.active()));return Map.of("updated",true);
 }
}
