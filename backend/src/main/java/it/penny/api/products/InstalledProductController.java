package it.penny.api.products;

import it.penny.api.security.PennyUser;
import it.penny.api.security.PennyUserRepository;
import it.penny.api.security.RbacService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

@RestController
@RequestMapping("/api/installed-products")
public class InstalledProductController {
 private final JdbcTemplate db;
 private final NamedParameterJdbcTemplate named;
 private final PennyUserRepository users;
 private final RbacService rbac;
 private final it.penny.api.common.AuditLog audit;
 public InstalledProductController(JdbcTemplate db,PennyUserRepository users,RbacService rbac,it.penny.api.common.AuditLog audit){this.audit=audit;this.db=db;this.named=new NamedParameterJdbcTemplate(db);this.users=users;this.rbac=rbac;}
 private void require(Authentication auth,String permission){PennyUser u=auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null);rbac.require(u,permission);}
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private String clean(String value,int max){if(value==null)return null;String s=value.trim();if(s.length()>max)throw bad("Campo troppo lungo: "+max);return s.isEmpty()?null:s;}
 private String required(String value,int max){String s=clean(value,max);if(s==null)throw bad("Nome obbligatorio");return s;}
 private void productExists(long id){if(id<1||db.queryForObject("SELECT count(*) FROM penny_installed_products WHERE id=? AND deleted_at IS NULL",Integer.class,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Prodotto non trovato");}
 private void installedCategory(String category){if(category==null||!Set.of("SOFTWARE","HARDWARE","SERVICE").contains(category))throw bad("Categoria non installabile");}
 private void customerExists(long id){if(id<1||db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=? AND deleted_at IS NULL",Integer.class,id)==0)throw bad("Cliente inesistente");}
 public record ProductInput(Long customerId,String category,String name,String manufacturer,String model,String serialNumber,String licenseKey,LocalDate installedOn,LocalDate warrantyUntil,LocalDate licenseExpiresOn,String status,String notes,String origin,Long catalogProductId,Long workstationId,JsonNode supplier,JsonNode quantity,JsonNode activatedUsers,JsonNode productTypeId,JsonNode business){}
 private MapSqlParameterSource params(ProductInput input,Long previousCatalogId,Long previousWorkstationId,Authentication auth,Map<String,Object> current){
  if(input==null||input.customerId()==null)throw bad("Cliente obbligatorio");customerExists(input.customerId());
  installedCategory(input.category());CatalogProductController.origin(input.origin());
  if("INTERNAL".equals(input.origin())&&input.catalogProductId()==null)throw bad("I prodotti interni richiedono un prodotto di catalogo");
  if(input.catalogProductId()!=null){
   boolean newLink=!Objects.equals(previousCatalogId,input.catalogProductId());
   if(newLink)require(auth,"CATALOG_READ");
   var matches=db.queryForList("SELECT origin,category,active FROM penny_catalog_products WHERE id=? AND deleted_at IS NULL"+(newLink?" FOR UPDATE":""),input.catalogProductId());
   if(matches.isEmpty())throw bad("Prodotto di catalogo inesistente");
   var item=matches.getFirst();
   if(!input.origin().equals(item.get("origin"))||!input.category().equals(item.get("category")))throw bad("Origine e categoria devono coincidere con il catalogo");
   if(newLink&&!Boolean.TRUE.equals(item.get("active")))throw bad("Prodotto di catalogo archiviato");
  }
  if(!Objects.equals(previousWorkstationId,input.workstationId()))require(auth,"WORKSTATIONS_READ");
  if(input.workstationId()!=null){
   boolean newStation=!Objects.equals(previousWorkstationId,input.workstationId());
   if(newStation)require(auth,"WORKSTATIONS_READ");
   var stations=db.queryForList("SELECT customer_id,active FROM penny_workstations WHERE id=? AND deleted_at IS NULL FOR UPDATE",input.workstationId());
   if(stations.isEmpty()||!Objects.equals(stations.getFirst().get("customer_id"),input.customerId()))throw bad("Postazione inesistente o di un altro cliente");
   if(newStation&&!Boolean.TRUE.equals(stations.getFirst().get("active")))throw bad("Postazione archiviata");
  }
  String status=input.status()==null?"ACTIVE":input.status();if(!Set.of("ACTIVE","MAINTENANCE","RETIRED").contains(status))throw bad("Stato non valido");
  if(input.installedOn()!=null&&input.warrantyUntil()!=null&&input.warrantyUntil().isBefore(input.installedOn()))throw bad("Garanzia precedente all'installazione");
  var previousBusiness=InstalledBusiness.parse(current==null?null:current.get("business"));
  if(input.business()!=null){for(String key:List.of("salePrice","purchasePrice")){var incoming=input.business().isNull()?com.fasterxml.jackson.databind.node.NullNode.instance:input.business().get(key);if(incoming!=null&&!Objects.equals(incoming,previousBusiness.get(key))&&!(incoming.isNull()&&!previousBusiness.hasNonNull(key)))require(auth,"SALES_WRITE");}}
  String notes=clean(input.notes(),10000);
  Long typeId=current==null?null:(Long)current.get("product_type_id");
  if(input.productTypeId()!=null){if(input.productTypeId().isNull())typeId=null;else{if(!input.productTypeId().isIntegralNumber()||!input.productTypeId().canConvertToLong()||input.productTypeId().longValue()<1)throw bad("Tipologia non valida");typeId=input.productTypeId().longValue();}}
  if(typeId==null&&input.catalogProductId()!=null)typeId=db.queryForObject("SELECT product_type_id FROM penny_catalog_products WHERE id=?",Long.class,input.catalogProductId());
  if(typeId!=null){var matches=db.queryForList("SELECT t.obsolete,c.active,c.operational_kind,c.legacy_category FROM penny_product_types t JOIN penny_product_categories c ON c.id=t.category_id WHERE t.id=? FOR SHARE OF t,c",typeId);if(matches.isEmpty())throw bad("Tipologia inesistente");var t=matches.getFirst();if(!(input.category().equals(t.get("operational_kind"))||("SERVICE".equals(input.category())&&"OTHER".equals(t.get("operational_kind"))&&"SERVICE".equals(t.get("legacy_category")))))throw bad("Tipologia incompatibile con la categoria");if(!Objects.equals(typeId,current==null?null:current.get("product_type_id"))){require(auth,"CATALOG_READ");if(Boolean.TRUE.equals(t.get("obsolete"))||!Boolean.TRUE.equals(t.get("active")))throw bad("Tipologia obsoleta o categoria archiviata");}}
  var links=current==null?List.<Map<String,Object>>of():saleLinks(((Number)current.get("id")).longValue());
  var business=InstalledBusiness.validate(input.business(),current==null?null:current.get("business"),input.category(),input.licenseExpiresOn(),!links.isEmpty());
  LocalDate effectiveSold=links.size()==1?((java.sql.Date)links.getFirst().get("soldOn")).toLocalDate():links.isEmpty()?InstalledBusiness.date(business,"soldOn"):null;
  LocalDate warranty=input.warrantyUntil();
  if("HARDWARE".equals(input.category())&&business.hasNonNull("warrantyYears"))warranty=effectiveSold==null?null:effectiveSold.plusYears(business.get("warrantyYears").intValue());
  return new MapSqlParameterSource().addValue("supplier",supplier(input.supplier(),current==null?null:current.get("supplier"))).addValue("quantity",count(input.quantity(),current==null?null:current.get("quantity"),1)).addValue("users",count(input.activatedUsers(),current==null?null:current.get("activated_users"),0)).addValue("customer",input.customerId()).addValue("category",input.category()).addValue("name",required(input.name(),200))
   .addValue("manufacturer",clean(input.manufacturer(),120)).addValue("model",clean(input.model(),120)).addValue("serial",clean(input.serialNumber(),160))
   .addValue("license",clean(input.licenseKey(),240)).addValue("installed",input.installedOn()).addValue("warranty",warranty)
   .addValue("expiry",input.licenseExpiresOn()).addValue("status",status).addValue("notes",notes).addValue("origin",input.origin()).addValue("catalog",input.catalogProductId()).addValue("workstation",input.workstationId()).addValue("type",typeId).addValue("business",business.toString());
 }
 private String supplier(JsonNode node,Object previous){if(node==null)return (String)previous;if(node.isNull())return null;if(!node.isTextual())throw bad("Fornitore non valido");return clean(node.textValue(),200);}
 private Integer count(JsonNode node,Object previous,int min){if(node==null)return previous==null?null:((Number)previous).intValue();if(node.isNull())return null;if(!node.isIntegralNumber()||!node.canConvertToInt()||node.intValue()<min||node.intValue()>1000000)throw bad("Quantità o utenti attivati non validi");return node.intValue();}
 private void stationAudit(Authentication auth,long id,Object before,Object after){var metadata=new LinkedHashMap<String,Object>();metadata.put("beforeWorkstationId",before);metadata.put("afterWorkstationId",after);audit.record(auth,"INSTALLATION_WORKSTATION_CHANGED","INSTALLATION",id,metadata);}
 private List<Map<String,Object>> saleLinks(long id){return db.queryForList("SELECT s.id AS \"saleId\",s.sold_on AS \"soldOn\",s.unit_price::text AS \"unitPrice\",a.quantity FROM penny_sale_allocations a JOIN penny_sales s ON s.id=a.sale_id WHERE a.installed_product_id=? ORDER BY s.sold_on,s.id",id);}
 private void stationName(Map<String,Object> row,Authentication auth){
  row.put("business",InstalledBusiness.parse(row.get("business")));
  var links=saleLinks(((Number)row.get("id")).longValue());row.put("saleLinks",links);
  var business=(com.fasterxml.jackson.databind.node.ObjectNode)row.get("business");if(!links.isEmpty())business.put("saleSource","OUR_SALE");
  row.put("effectiveSoldOn",links.size()==1?links.getFirst().get("soldOn"):links.isEmpty()?InstalledBusiness.text(business,"soldOn"):null);
  row.put("effectivePrice",links.size()==1?links.getFirst().get("unitPrice"):links.isEmpty()?InstalledBusiness.text(business,"salePrice"):null);
  var financialUser=auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null);boolean financialRead=financialUser!=null&&rbac.allowed(financialUser.id,"SALES_READ");row.put("financialRead",financialRead);if(!financialRead){business.remove(List.of("salePrice","purchasePrice"));row.put("effectivePrice",null);links.forEach(link->link.put("unitPrice",null));}
  var typeNames=row.get("productTypeId")==null?List.<String>of():db.queryForList("SELECT name FROM penny_product_types WHERE id=?",String.class,row.get("productTypeId"));row.put("typeName",typeNames.isEmpty()?null:typeNames.getFirst());
  row.put("workstationName",null);PennyUser u=auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null);
  if(u!=null&&rbac.allowed(u.id,"WORKSTATIONS_READ")&&row.get("workstationId")!=null){var names=db.queryForList("SELECT name FROM penny_workstations WHERE id=?",String.class,row.get("workstationId"));if(!names.isEmpty())row.put("workstationName",names.getFirst());}
 }
 private static final String FIELDS="id,product_type_id AS \"productTypeId\",business::text AS business,supplier,quantity,activated_users AS \"activatedUsers\",workstation_id AS \"workstationId\",origin,catalog_product_id AS \"catalogProductId\",customer_id AS \"customerId\",category,name,manufacturer,model,serial_number AS \"serialNumber\",license_key AS \"licenseKey\",installed_on AS \"installedOn\",penny_installed_warranty(id,business,warranty_until) AS \"warrantyUntil\",license_expires_on AS \"licenseExpiresOn\",status,notes,created_at AS \"createdAt\",updated_at AS \"updatedAt\"";
 @GetMapping public Map<String,Object> list(@RequestParam(required=false) Long customerId,@RequestParam(required=false) String category,@RequestParam(defaultValue="") String q,@RequestParam(required=false) Long typeId,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="asc") String direction,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
  require(auth,"PRODUCTS_READ");if(category!=null)installedCategory(category);if(page<0||page>100000||size<1||size>100)throw bad("Paginazione non valida");
  String order=Map.of("name","lower(name)","status","status","createdAt","created_at","quantity","quantity").get(sort);if(order==null||!Set.of("asc","desc").contains(direction))throw bad("Ordinamento non valido");
  String search=q.trim();if(search.length()>100)throw bad("Ricerca troppo lunga");String like="%"+search.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  String where=" WHERE deleted_at IS NULL"+(typeId==null?"":" AND product_type_id="+typeId.longValue())+" AND (CAST(? AS bigint) IS NULL OR customer_id=?) AND (CAST(? AS varchar) IS NULL OR category=?) AND (lower(name) LIKE ? ESCAPE '\\' OR lower(coalesce(serial_number,'')) LIKE ? ESCAPE '\\' OR lower(coalesce(manufacturer,'')) LIKE ? ESCAPE '\\')";
  Long total=db.queryForObject("SELECT count(*) FROM penny_installed_products"+where,Long.class,customerId,customerId,category,category,like,like,like);
  var items=db.queryForList("SELECT "+FIELDS+" FROM penny_installed_products"+where+" ORDER BY "+order+" "+direction+" NULLS LAST,id ASC LIMIT ? OFFSET ?",customerId,customerId,category,category,like,like,like,size,page*size);
  items.forEach(row->stationName(row,auth));
  return Map.of("items",items,"total",total==null?0:total,"page",page,"size",size);
 }
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable long id,Authentication auth){require(auth,"PRODUCTS_READ");productExists(id);var row=db.queryForMap("SELECT "+FIELDS+" FROM penny_installed_products WHERE id=? AND deleted_at IS NULL",id);stationName(row,auth);return row;}
 @PostMapping @Transactional public Map<String,Object> create(@RequestBody ProductInput input,Authentication auth){
  require(auth,"PRODUCTS_WRITE");var p=params(input,null,null,auth,null);GeneratedKeyHolder key=new GeneratedKeyHolder();
  try{named.update("INSERT INTO penny_installed_products(customer_id,category,name,manufacturer,model,serial_number,license_key,installed_on,warranty_until,license_expires_on,status,notes,origin,catalog_product_id,workstation_id,supplier,quantity,activated_users,product_type_id,business) VALUES (:customer,:category,:name,:manufacturer,:model,:serial,:license,:installed,:warranty,:expiry,:status,:notes,:origin,:catalog,:workstation,:supplier,:quantity,:users,:type,CAST(:business AS jsonb))",p,key,new String[]{"id"});}
  catch(DataIntegrityViolationException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Matricola duplicata o collegamenti incompatibili: libera le allocazioni prima di dismettere o cambiare cliente/prodotto");}
  long id=Objects.requireNonNull(key.getKey()).longValue();if(input.workstationId()!=null)stationAudit(auth,id,null,input.workstationId());
  audit.record(auth,"INSTALLATION_CREATED","INSTALLATION",id,Map.of("category",input.category(),"origin",input.origin()));return Map.of("id",id);
 }
 @PutMapping("/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,@RequestBody ProductInput input,Authentication auth){
  require(auth,"PRODUCTS_WRITE");productExists(id);var current=db.queryForMap("SELECT id,catalog_product_id,workstation_id,supplier,quantity,activated_users,product_type_id,business::text AS business FROM penny_installed_products WHERE id=? AND deleted_at IS NULL FOR UPDATE",id);
  var p=params(input,(Long)current.get("catalog_product_id"),(Long)current.get("workstation_id"),auth,current).addValue("id",id);
  try{named.update("UPDATE penny_installed_products SET customer_id=:customer,category=:category,name=:name,manufacturer=:manufacturer,model=:model,serial_number=:serial,license_key=:license,installed_on=:installed,warranty_until=:warranty,license_expires_on=:expiry,status=:status,notes=:notes,origin=:origin,catalog_product_id=:catalog,workstation_id=:workstation,supplier=:supplier,quantity=:quantity,activated_users=:users,product_type_id=:type,business=CAST(:business AS jsonb),updated_at=now() WHERE id=:id",p);}
  catch(DataIntegrityViolationException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Matricola duplicata o collegamenti incompatibili: libera le allocazioni prima di dismettere o cambiare cliente/prodotto");}
  if(!Objects.equals(current.get("workstation_id"),input.workstationId()))stationAudit(auth,id,current.get("workstation_id"),input.workstationId());
  audit.record(auth,"INSTALLATION_UPDATED","INSTALLATION",id,Map.of("category",input.category(),"origin",input.origin()));return Map.of("updated",true);
 }
 public record BulkRelease(Long customerId,Long typeId,List<Long> ids,String release,LocalDate changedOn){}
 @PatchMapping("/bulk-release") @Transactional public Map<String,Object> bulkRelease(@RequestBody BulkRelease input,Authentication auth){
  require(auth,"PRODUCTS_WRITE");
  if(input==null||input.customerId()==null||input.typeId()==null||input.ids()==null||input.ids().isEmpty()||input.ids().size()>500||input.changedOn()==null)throw bad("Cliente, tipologia, selezione (massimo 500), release e data obbligatori");
  String release=required(input.release(),240);customerExists(input.customerId());
  if(input.ids().stream().anyMatch(id->id==null||id<1))throw bad("Selezione non valida");
  var ids=input.ids().stream().distinct().sorted().toList();
  var params=new MapSqlParameterSource().addValue("ids",ids);
  var rows=named.queryForList("SELECT id,customer_id,product_type_id,category,business::text AS business FROM penny_installed_products WHERE deleted_at IS NULL AND id IN (:ids) ORDER BY id FOR UPDATE",params);
  if(rows.size()!=ids.size()||rows.stream().anyMatch(row->!Objects.equals(input.customerId(),row.get("customer_id"))||!Objects.equals(input.typeId(),row.get("product_type_id"))||!"SOFTWARE".equals(row.get("category"))))throw bad("Tutti i prodotti devono essere software attivi dello stesso cliente e tipologia");
  for(var row:rows){var business=InstalledBusiness.parse(row.get("business"));business.put("usageRelease",release);business.put("releaseUpdatedOn",input.changedOn().toString());db.update("UPDATE penny_installed_products SET business=?::jsonb,updated_at=now() WHERE id=?",business.toString(),row.get("id"));}
  audit.record(auth,"INSTALLATION_RELEASE_BULK_UPDATED","CUSTOMER",input.customerId(),Map.of("ids",ids,"typeId",input.typeId(),"release",release,"changedOn",input.changedOn().toString()));
  return Map.of("updated",rows.size());
 }

}
