package it.penny.api.customerworkspace;

import it.penny.api.security.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.penny.api.common.AuditLog;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
public class CustomerWorkspaceController {
 private final ObjectMapper json; private final JdbcTemplate db; private final PennyUserRepository users; private final RbacService rbac; private final AuditLog audit;
 public CustomerWorkspaceController(JdbcTemplate db,PennyUserRepository users,RbacService rbac,AuditLog audit,ObjectMapper json){this.json=json;this.db=db;this.users=users;this.rbac=rbac;this.audit=audit;}
 private PennyUser user(Authentication auth){return auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null);}
 private void require(Authentication auth,String... permissions){var u=user(auth);for(var p:permissions)rbac.require(u,p);}
 private boolean allowed(Authentication auth,String permission){var u=user(auth);return u!=null&&u.enabled&&!u.mustChangePassword&&rbac.allowed(u.id,permission);}
 private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private void customer(Long id){if(id==null||id<1||db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=? AND deleted_at IS NULL",Integer.class,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente non trovato");}
 private static String clean(String value,int max){if(value==null)return null;var s=value.trim();if(s.length()>max)throw bad("Campo troppo lungo: "+max);return s.isEmpty()?null:s;}
 private static final String SITE_FIELDS="id,customer_id AS \"customerId\",name,street,city,province,notes,site_types::text AS \"siteTypes\"";
 private List<Map<String,Object>> sites(long id){var rows=db.queryForList("SELECT "+SITE_FIELDS+" FROM penny_customer_sites WHERE customer_id=? ORDER BY name,id",id);for(var row:rows){try{row.put("siteTypes",json.readValue((String)row.get("siteTypes"),json.getTypeFactory().constructCollectionType(List.class,String.class)));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid stored site types",e);}}return rows;}
 public record SiteInput(Long customerId,String name,String street,String city,String notes,JsonNode siteTypes,JsonNode province){}
 public record Assignment(Long siteId){}
 private static final Set<String> SITE_TYPES=Set.of("LEGAL","ADMINISTRATIVE","OPERATIONAL","COMMERCIAL","WAREHOUSE");
 private String siteTypes(JsonNode types){
  if(types==null)return null;
  if(!types.isArray()||types.size()>5)throw bad("Tipologie sede non valide");
  Set<String> seen=new HashSet<>();for(var type:types)if(!type.isTextual()||!SITE_TYPES.contains(type.textValue())||!seen.add(type.textValue()))throw bad("Tipologie sede non valide");
  return types.toString();
 }

 private String province(JsonNode value){if(value==null||value.isNull())return null;if(!value.isTextual())throw bad("Provincia non valida");return clean(value.asText(),100);}
 private Object[] values(SiteInput input){if(input==null)throw bad("Dati obbligatori");customer(input.customerId());var name=clean(input.name(),200);if(name==null)throw bad("Nome sede obbligatorio");return new Object[]{input.customerId(),name,clean(input.street(),240),clean(input.city(),120),clean(input.notes(),10000)};}
 @GetMapping("/api/customer-sites") public Map<String,Object> list(@RequestParam long customerId,Authentication auth){require(auth,"CUSTOMERS_READ");customer(customerId);var items=sites(customerId);return Map.of("items",items,"total",items.size());}
 @PostMapping("/api/customer-sites") @Transactional public Map<String,Object> create(@RequestBody SiteInput input,Authentication auth){require(auth,"CUSTOMERS_WRITE","CUSTOMERS_READ");var vals=values(input);var types=siteTypes(input.siteTypes());var id=db.queryForObject("INSERT INTO penny_customer_sites(customer_id,name,street,city,notes,site_types,province) VALUES (?,?,?,?,?,?::jsonb,?) RETURNING id",Long.class,vals[0],vals[1],vals[2],vals[3],vals[4],types==null?"[]":types,province(input.province()));audit.record(auth,"CUSTOMER_SITE_CREATED","CUSTOMER_SITE",id,Map.of("customerId",input.customerId()));return Map.of("id",id);}
 @PutMapping("/api/customer-sites/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,@RequestBody SiteInput input,Authentication auth){require(auth,"CUSTOMERS_WRITE","CUSTOMERS_READ");var current=db.queryForList("SELECT customer_id,province FROM penny_customer_sites WHERE id=? FOR UPDATE",id);if(current.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Sede non trovata");var vals=values(input);if(!Objects.equals(current.getFirst().get("customer_id"),input.customerId()))throw bad("La sede non può cambiare cliente");db.update("UPDATE penny_customer_sites SET customer_id=?,name=?,street=?,city=?,notes=?,site_types=coalesce(?::jsonb,site_types),province=?,updated_at=now() WHERE id=?",vals[0],vals[1],vals[2],vals[3],vals[4],siteTypes(input.siteTypes()),input.province()==null?current.getFirst().get("province"):province(input.province()),id);audit.record(auth,"CUSTOMER_SITE_UPDATED","CUSTOMER_SITE",id,Map.of("customerId",input.customerId()));return Map.of("updated",true);}
 @PatchMapping("/api/customer-sites/assign-product/{id}") @Transactional public Map<String,Object> assign(@PathVariable long id,@RequestBody Assignment input,Authentication auth){require(auth,"PRODUCTS_WRITE","CUSTOMERS_READ");if(input==null)throw bad("Dati obbligatori");var products=db.queryForList("SELECT customer_id,site_id FROM penny_installed_products WHERE id=? AND deleted_at IS NULL FOR UPDATE",id);if(products.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Prodotto non trovato");var product=products.getFirst();customer((Long)product.get("customer_id"));if(input.siteId()!=null&&db.queryForObject("SELECT count(*) FROM penny_customer_sites WHERE id=? AND customer_id=?",Integer.class,input.siteId(),product.get("customer_id"))==0)throw bad("Sede inesistente o di un altro cliente");db.update("UPDATE penny_installed_products SET site_id=?,updated_at=now() WHERE id=?",input.siteId(),id);var meta=new LinkedHashMap<String,Object>();meta.put("beforeSiteId",product.get("site_id"));meta.put("afterSiteId",input.siteId());audit.record(auth,"INSTALLATION_SITE_CHANGED","INSTALLATION",id,meta);return Map.of("updated",true);}

 @GetMapping("/api/customer-organization/{customerId}") public Map<String,Object> organization(@PathVariable long customerId,Authentication auth){
  require(auth,"CUSTOMERS_READ");customer(customerId);
  var customer=db.queryForMap("SELECT id,name,kind,lifecycle_status AS \"lifecycleStatus\",archived FROM penny_customers WHERE id=? AND deleted_at IS NULL",customerId);
  var contacts=db.queryForList("SELECT c.id,c.full_name AS \"fullName\",c.email,c.phone,l.site_id AS \"siteId\",l.role,l.is_primary AS \"isPrimary\",(SELECT count(*) FROM penny_contact_customers cl JOIN penny_customers cu ON cu.id=cl.customer_id WHERE cl.contact_id=c.id AND cu.deleted_at IS NULL) AS \"customerCount\" FROM penny_contact_customers l JOIN penny_customer_contacts c ON c.id=l.contact_id WHERE l.customer_id=? AND c.deleted_at IS NULL ORDER BY c.full_name,c.id",customerId);
  return Map.of("customer",customer,"sites",sites(customerId),"contacts",contacts);
 }
 private static final String DEADLINES="SELECT p.id AS \"productId\",p.name AS \"productName\",p.customer_id AS \"customerId\",c.name AS \"customerName\",p.site_id AS \"siteId\",p.origin,d.type,d.date FROM penny_installed_products p JOIN penny_customers c ON c.id=p.customer_id CROSS JOIN LATERAL (VALUES ('WARRANTY',penny_installed_warranty(p.id,p.business,p.warranty_until)),('LICENSE',CASE WHEN coalesce(p.business->>'softwareStatus','') NOT IN ('CANCELLED','OTHER_RESELLER') AND coalesce(p.business->>'licenseType','')<>'PERPETUAL' THEN p.license_expires_on END),('HARDWARE_TERM',CASE WHEN p.category='HARDWARE' AND p.business->>'saleType' IN ('RENTAL','DEMO') AND coalesce(p.business->>'hardwareStatus','')<>'OBSOLETE' THEN (p.business->>'expiresOn')::date END)) d(type,date) WHERE p.deleted_at IS NULL AND c.deleted_at IS NULL AND d.date IS NOT NULL";
 static void dateRange(LocalDate from,LocalDate to){if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365)throw bad("Intervallo date non valido (massimo 366 giorni)");}
 @GetMapping("/api/deadlines") public Map<String,Object> deadlines(@RequestParam LocalDate from,@RequestParam LocalDate to,Authentication auth){require(auth,"CUSTOMERS_READ","PRODUCTS_READ");dateRange(from,to);var items=db.queryForList(DEADLINES+" AND d.date>=? AND d.date<=? ORDER BY d.date,p.id,d.type",from,to);return Map.of("items",items,"total",items.size(),"from",from,"to",to);}
 @GetMapping("/api/customer-workspace/{customerId}") public Map<String,Object> workspace(@PathVariable long customerId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size,Authentication auth){
  require(auth,"CUSTOMERS_READ");customer(customerId);if(page<0||page>100000||size<1||size>100)throw bad("Paginazione non valida");boolean productsRead=allowed(auth,"PRODUCTS_READ"),stationsRead=allowed(auth,"WORKSTATIONS_READ");
  var result=new LinkedHashMap<String,Object>();result.put("sites",sites(customerId));result.put("permissions",Map.of("productsRead",productsRead,"workstationsRead",stationsRead));result.put("page",page);result.put("size",size);
  result.put("products",productsRead?db.queryForList("SELECT id,product_type_id AS \"productTypeId\",(SELECT name FROM penny_product_types WHERE id=penny_installed_products.product_type_id) AS \"typeName\",business::text AS business,customer_id AS \"customerId\",site_id AS \"siteId\",workstation_id AS \"workstationId\",name,category,origin,status,supplier,quantity,activated_users AS \"activatedUsers\",installed_on AS \"installedOn\",penny_installed_warranty(id,business,warranty_until) AS \"warrantyUntil\",license_expires_on AS \"licenseExpiresOn\" FROM penny_installed_products WHERE customer_id=? AND deleted_at IS NULL ORDER BY lower(name),id LIMIT ? OFFSET ?",customerId,size,page*size):List.of());
  if(productsRead){ @SuppressWarnings("unchecked") var installed=(List<Map<String,Object>>)result.get("products"); installed.forEach(p->{try{p.put("business",json.readTree((String)p.get("business")));}catch(Exception e){throw new IllegalStateException(e);}}); }
  if(!stationsRead){ @SuppressWarnings("unchecked") var products=(List<Map<String,Object>>)result.get("products"); products.forEach(p->p.remove("workstationId")); }
  result.put("productsTotal",productsRead?db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE customer_id=? AND deleted_at IS NULL",Long.class,customerId):0);
  result.put("workstations",stationsRead?db.queryForList("SELECT id,customer_id AS \"customerId\",site_id AS \"siteId\",name,location,active FROM penny_workstations WHERE customer_id=? AND deleted_at IS NULL ORDER BY id DESC LIMIT ? OFFSET ?",customerId,size,page*size):List.of());
  result.put("workstationsTotal",stationsRead?db.queryForObject("SELECT count(*) FROM penny_workstations WHERE customer_id=? AND deleted_at IS NULL",Long.class,customerId):0);
  result.put("summary",productsRead?db.queryForMap("SELECT count(*) FILTER (WHERE category='SOFTWARE') AS software,count(*) FILTER (WHERE category='HARDWARE') AS hardware,count(*) FILTER (WHERE origin='THIRD_PARTY') AS \"thirdParty\" FROM penny_installed_products WHERE customer_id=? AND deleted_at IS NULL",customerId):Map.of());
  var combined=new ArrayList<Map<String,Object>>();if(productsRead)combined.addAll(db.queryForList(DEADLINES+" AND p.customer_id=? ORDER BY d.date,p.id,d.type",customerId));
  var kinds=new ArrayList<String>();if(allowed(auth,"TRAINING_READ"))kinds.add("TRAINING");if(allowed(auth,"CONTRACTS_READ"))kinds.add("CONTRACT");if(allowed(auth,"ACTIVITIES_READ"))kinds.addAll(List.of("DEVELOPMENT","TECHNICAL","COMMERCIAL"));
  var recordSummary=new LinkedHashMap<String,Object>();if(!kinds.isEmpty()){var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db);var args=new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("customer",customerId).addValue("kinds",kinds);for(var row:named.queryForList("SELECT kind,count(*) total FROM penny_customer_records WHERE customer_id=:customer AND kind IN (:kinds) AND deleted_at IS NULL GROUP BY kind",args))recordSummary.put((String)row.get("kind"),row.get("total"));combined.addAll(named.queryForList("SELECT id AS \"recordId\",name AS \"productName\",kind AS type,coalesce(expires_on,starts_on) AS date FROM penny_customer_records WHERE customer_id=:customer AND kind IN (:kinds) AND deleted_at IS NULL AND status<>'CANCELLED' AND coalesce(expires_on,starts_on) IS NOT NULL",args));}
  result.put("recordSummary",recordSummary);combined.sort(Comparator.comparing(row->row.get("date").toString()));result.put("deadlines",combined);
  if(!allowed(auth,"SALES_READ")){@SuppressWarnings("unchecked")var products=(List<Map<String,Object>>)result.get("products");for(var product:products){if(product.get("business") instanceof com.fasterxml.jackson.databind.node.ObjectNode business)business.remove(List.of("salePrice","purchasePrice"));}}
  return result;
 }
}
