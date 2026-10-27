package it.penny.api.records;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import it.penny.api.common.AuditLog;
import it.penny.api.security.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class CustomerRecordService {
 private final JdbcTemplate db;private final NamedParameterJdbcTemplate named;private final PennyUserRepository users;private final RbacService rbac;private final AuditLog audit;private final ObjectMapper json;
 public CustomerRecordService(JdbcTemplate db,PennyUserRepository users,RbacService rbac,AuditLog audit,ObjectMapper json){this.db=db;this.named=new NamedParameterJdbcTemplate(db);this.users=users;this.rbac=rbac;this.audit=audit;this.json=json;}
 static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 public static String permission(String kind){return switch(kind==null?"":kind){case "TRAINING"->"TRAINING";case "CONTRACT"->"CONTRACTS";case "DEVELOPMENT","TECHNICAL","COMMERCIAL"->"ACTIVITIES";default->throw bad("Tipo scheda non valido");};}
 private PennyUser user(Authentication auth){return auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null);}
 public void require(Authentication auth,String permission){rbac.require(user(auth),permission);}
 private static String text(String value,int max,boolean required){if(value==null){if(required)throw bad("Campo obbligatorio");return null;}String s=value.trim();if(s.length()>max||required&&s.isEmpty())throw bad("Testo non valido");return s.isEmpty()?null:s;}
 private static void option(ObjectNode details,String key,Set<String> choices){JsonNode n=details.get(key);if(n!=null&&!n.isNull()&&(!n.isTextual()||!choices.contains(n.asText())))throw bad("Valore non valido: "+key);}
 private static BigDecimal decimal(ObjectNode details,String key){JsonNode n=details.get(key);if(n==null||n.isNull())return null;if(!n.isTextual()&&!n.isNumber())throw bad("Importo non valido: "+key);try{BigDecimal value=new BigDecimal(n.asText());if(value.signum()<0||value.scale()>2||value.compareTo(new BigDecimal("999999999.99"))>0)throw bad("Importo non valido: "+key);details.put(key,value.toPlainString());return value;}catch(NumberFormatException ex){throw bad("Numero non valido: "+key);}}
 public static ObjectNode validateDetails(String kind,JsonNode node){
  permission(kind);if(node!=null&&!node.isObject())throw bad("Dettagli non validi");ObjectNode d=node==null?JsonNodeFactory.instance.objectNode():((ObjectNode)node).deepCopy();
  Set<String> keys=switch(kind){case "TRAINING"->Set.of("venue","periodText","teacherText","participants","soldOn","price");case "CONTRACT"->Set.of("contractType","totalHours","remainingHours","soldOn","price","collaboratorCost","supplyChannel");default->Set.of("responsibleText");};
  d.fieldNames().forEachRemaining(key->{if(!keys.contains(key))throw bad("Campo dettagli sconosciuto: "+key);});
  for(String key:List.of("periodText","teacherText","responsibleText")){var n=d.get(key);if(n!=null&&!n.isNull()){if(!n.isTextual())throw bad("Testo non valido: "+key);d.put(key,text(n.asText(),2000,false));}}
  if("TRAINING".equals(kind)){
   option(d,"venue",Set.of("ONLINE","OUR_SITE","CUSTOMER_SITE","EXTERNAL"));
   var participants=d.get("participants");if(participants!=null){if(!participants.isArray()||participants.size()>500)throw bad("Partecipanti non validi");for(var p:participants){if(!p.isObject())throw bad("Partecipante non valido");p.fieldNames().forEachRemaining(key->{if(!Set.of("contactId","name").contains(key))throw bad("Campo partecipante sconosciuto");});var id=p.get("contactId");if(id!=null&&!id.isNull()&&(!id.isIntegralNumber()||!id.canConvertToLong()||id.longValue()<1))throw bad("Contatto non valido");var name=p.get("name");if(name!=null&&!name.isNull()&&!name.isTextual())throw bad("Nome partecipante non valido");if((id==null||id.isNull())&&(name==null||name.isNull()||text(name.asText(),200,false)==null))throw bad("Nome partecipante obbligatorio");}}
  }
  if("CONTRACT".equals(kind)){
   option(d,"supplyChannel",Set.of("INTERNAL_SALE","EXTERNAL_SUPPLY"));
   option(d,"contractType",Set.of("GENERAL","PHONE","UPDATES","HOURLY"));
   BigDecimal total=decimal(d,"totalHours"),remaining=decimal(d,"remainingHours");
   if(!"HOURLY".equals(d.path("contractType").asText())&&(total!=null||remaining!=null))throw bad("Le ore richiedono un contratto orario");
   if(remaining!=null&&(total==null||remaining.compareTo(total)>0))throw bad("Le ore residue superano le ore totali");
  }
  if(keys.contains("price"))decimal(d,"price");if(keys.contains("collaboratorCost"))decimal(d,"collaboratorCost");
  if(d.hasNonNull("soldOn")){if(!d.get("soldOn").isTextual())throw bad("Data vendita non valida");try{LocalDate.parse(d.get("soldOn").asText());}catch(Exception ex){throw bad("Data vendita non valida");}}
  return d;
 }
 static ObjectNode saleDetails(String kind,long customerId,boolean newLink,Map<String,?> sale,ObjectNode details){
  if(!Set.of("TRAINING","CONTRACT").contains(kind)||!kind.equals(sale.get("operational_kind")))throw bad("Vendita incompatibile con il tipo di scheda");
  if(!(sale.get("customer_id") instanceof Number owner)||owner.longValue()!=customerId)throw bad("Vendita di un altro cliente");
  if(newLink&&!"CONFIRMED".equals(sale.get("status")))throw bad("Vendita annullata");
  ObjectNode result=details.deepCopy();result.put("soldOn",sale.get("sold_on").toString());result.put("price",new BigDecimal(sale.get("unit_price").toString()).toPlainString());return result;
 }
 private static final String FIELDS="r.id,r.customer_id AS \"customerId\",r.site_id AS \"siteId\",r.kind,r.name,(SELECT t.name FROM penny_product_types t WHERE t.id=r.product_type_id) AS \"productTypeName\",r.product_type_id AS \"productTypeId\",r.sale_id AS \"saleId\",r.starts_on AS \"startsOn\",r.expires_on AS \"expiresOn\",r.status,r.notes,r.details::text AS details,r.created_at AS \"createdAt\",r.updated_at AS \"updatedAt\"";
 private Map<String,Object> decode(Map<String,Object> row){try{ObjectNode details=(ObjectNode)json.readTree((String)row.get("details"));if("TRAINING".equals(row.get("kind"))){var participants=db.queryForList("SELECT contact_id AS \"contactId\",name FROM penny_record_participants WHERE record_id=? ORDER BY id",row.get("id"));details.set("participants",json.valueToTree(participants));}if(row.get("saleId")!=null){var sale=db.queryForMap("SELECT sold_on,unit_price FROM penny_sales WHERE id=?",row.get("saleId"));details.put("soldOn",sale.get("sold_on").toString());details.put("price",new BigDecimal(sale.get("unit_price").toString()).toPlainString());}row.put("details",details);return row;}catch(Exception ex){throw new IllegalStateException("Dettagli non leggibili",ex);}}
 private Map<String,Object> row(long id,boolean lock){var rows=db.queryForList("SELECT "+FIELDS+" FROM penny_customer_records r WHERE r.id=? AND r.deleted_at IS NULL"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Scheda non trovata");return decode(rows.getFirst());}
 static ObjectNode readFinancials(ObjectNode value,boolean allowed){var result=value.deepCopy();if(!allowed)result.remove(List.of("price","collaboratorCost"));return result;}
 static ObjectNode writeFinancials(ObjectNode incoming,ObjectNode previous,boolean allowed,boolean linked){var result=incoming.deepCopy();for(String key:List.of("price","collaboratorCost")){if(result.has(key)&&!Objects.equals(result.get(key),previous.get(key))&&!(result.get(key).isNull()&&!previous.hasNonNull(key))&&!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Permesso vendite richiesto per modificare gli importi");if(!result.has(key)&&previous.has(key))result.set(key,previous.get(key));}return result;}
 private Map<String,Object> financial(Map<String,Object> row,Authentication auth){var u=user(auth);boolean read=u!=null&&rbac.allowed(u.id,"SALES_READ");row.put("financialRead",read);row.put("details",readFinancials((ObjectNode)row.get("details"),read));return row;}
 public Map<String,Object> get(long id,Authentication auth){var row=row(id,false);require(auth,permission((String)row.get("kind"))+"_READ");return financial(row,auth);}
 public Map<String,Object> list(String kind,Long customerId,String q,int page,int size,String sort,String dir,Authentication auth){
  require(auth,permission(kind)+"_READ");if(page<0||page>100000||size<1||size>100||q.length()>100)throw bad("Ricerca o paginazione non valida");
  String order=Map.of("name","lower(r.name)","startsOn","r.starts_on","expiresOn","r.expires_on","status","r.status","createdAt","r.created_at").get(sort);if(order==null||!Set.of("asc","desc").contains(dir))throw bad("Ordinamento non valido");
  String where=" WHERE r.deleted_at IS NULL AND r.kind=:kind AND (CAST(:customer AS bigint) IS NULL OR r.customer_id=:customer) AND lower(r.name) LIKE :q ESCAPE '\\'";
  String like="%"+q.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  var p=new MapSqlParameterSource("kind",kind).addValue("customer",customerId).addValue("q",like).addValue("size",size).addValue("offset",page*size);
  var items=named.queryForList("SELECT "+FIELDS+" FROM penny_customer_records r"+where+" ORDER BY "+order+" "+dir+" NULLS LAST,r.id LIMIT :size OFFSET :offset",p);items.forEach(item->{decode(item);financial(item,auth);});
  return Map.of("items",items,"total",Objects.requireNonNull(named.queryForObject("SELECT count(*) FROM penny_customer_records r"+where,p,Long.class)),"page",page,"size",size);
 }
 private MapSqlParameterSource params(CustomerRecordInput input,Map<String,Object> current,Authentication auth){
  if(input==null||input.customerId()==null)throw bad("Cliente obbligatorio");require(auth,permission(input.kind())+"_WRITE");
  if(current!=null&&!Objects.equals(current.get("kind"),input.kind()))throw bad("Il tipo della scheda non è modificabile");
  if(current==null||!Objects.equals(current.get("customerId"),input.customerId()))require(auth,"CUSTOMERS_READ");
  if(db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=? AND deleted_at IS NULL",Integer.class,input.customerId())==0)throw bad("Cliente inesistente");
  if(input.siteId()!=null&&db.queryForObject("SELECT count(*) FROM penny_customer_sites WHERE id=? AND customer_id=?",Integer.class,input.siteId(),input.customerId())==0)throw bad("Sede di un altro cliente");
  Map<String,Object> linkedSale=null;boolean newSaleLink=current==null||!Objects.equals(current.get("saleId"),input.saleId());
  if(input.saleId()!=null){if(newSaleLink)require(auth,"SALES_READ");var sales=db.queryForList("SELECT s.customer_id,s.status,s.sold_on,s.unit_price,c.operational_kind FROM penny_sales s JOIN penny_catalog_products p ON p.id=s.catalog_product_id JOIN penny_product_categories c ON c.id=p.category_id WHERE s.id=? FOR SHARE OF s",input.saleId());if(sales.isEmpty())throw bad("Vendita inesistente");linkedSale=sales.getFirst();}
  if("CONTRACT".equals(input.kind())&&input.productTypeId()==null&&(current==null||current.get("productTypeId")!=null))throw bad("Tipologia contratto obbligatoria");
  if(input.productTypeId()!=null){boolean changed=current==null||!Objects.equals(current.get("productTypeId"),input.productTypeId());if(changed)require(auth,"CATALOG_READ");var types=db.queryForList("SELECT c.operational_kind,t.obsolete,c.active FROM penny_product_types t JOIN penny_product_categories c ON c.id=t.category_id WHERE t.id=? FOR SHARE OF t,c",input.productTypeId());if(types.isEmpty()||!input.kind().equals(types.getFirst().get("operational_kind")))throw bad("Tipologia incompatibile");if(changed&&(Boolean.TRUE.equals(types.getFirst().get("obsolete"))||!Boolean.TRUE.equals(types.getFirst().get("active"))))throw bad("Tipologia obsoleta");}
  var status=input.status()==null?("CONTRACT".equals(input.kind())?"ACTIVE":"PLANNED"):input.status();if(!("CONTRACT".equals(input.kind())?Set.of("ACTIVE","EXPIRING","SUSPENDED","INSOLVENCY","EXPIRED","CANCELLED"):Set.of("PLANNED","IN_PROGRESS","COMPLETED","CANCELLED")).contains(status))throw bad("Stato non valido");
  if(input.startsOn()!=null&&input.expiresOn()!=null&&input.expiresOn().isBefore(input.startsOn()))throw bad("Scadenza precedente all'inizio");
  ObjectNode incoming=input.details()==null?JsonNodeFactory.instance.objectNode():input.details().deepCopy();
  var oldDetails=current==null?JsonNodeFactory.instance.objectNode():(ObjectNode)current.get("details");
  var financialUser=user(auth);incoming=writeFinancials(incoming,oldDetails,financialUser!=null&&rbac.allowed(financialUser.id,"SALES_WRITE"),input.saleId()!=null);
  ObjectNode details=validateDetails(input.kind(),incoming);if(linkedSale!=null)details=saleDetails(input.kind(),input.customerId(),newSaleLink,linkedSale,details);
  if("TRAINING".equals(input.kind()))for(var participant:details.path("participants")){if(participant.hasNonNull("contactId")){long id=participant.get("contactId").longValue();var names=db.queryForList("SELECT c.full_name FROM penny_customer_contacts c JOIN penny_contact_customers l ON l.contact_id=c.id WHERE c.id=? AND l.customer_id=? AND c.deleted_at IS NULL FOR SHARE OF c",String.class,id,input.customerId());if(names.isEmpty())throw bad("Partecipante non associato al cliente");((ObjectNode)participant).put("name",names.getFirst());}else ((ObjectNode)participant).put("name",text(participant.path("name").asText(),200,true));}
  String name=text(input.name(),200,!"CONTRACT".equals(input.kind()));
  if(name==null)name=input.productTypeId()!=null?db.queryForObject("SELECT name FROM penny_product_types WHERE id=?",String.class,input.productTypeId()):current==null?"Contratto":(String)current.get("name");
  return new MapSqlParameterSource("customer",input.customerId()).addValue("site",input.siteId()).addValue("kind",input.kind()).addValue("name",name).addValue("type",input.productTypeId()).addValue("sale",input.saleId()).addValue("starts",input.startsOn()).addValue("expires",input.expiresOn()).addValue("status",status).addValue("notes",text(input.notes(),10000,false)).addValue("details",details.toString());
 }
 private void participants(long id,CustomerRecordInput input){db.update("DELETE FROM penny_record_participants WHERE record_id=?",id);if(!"TRAINING".equals(input.kind()))return;var d=validateDetails(input.kind(),input.details());for(var p:d.path("participants")){Long contact=p.hasNonNull("contactId")?p.get("contactId").longValue():null;String name=contact==null?p.path("name").asText():db.queryForObject("SELECT full_name FROM penny_customer_contacts WHERE id=?",String.class,contact);db.update("INSERT INTO penny_record_participants(record_id,contact_id,name) VALUES (?,?,?)",id,contact,text(name,200,true));}db.update("UPDATE penny_customer_records SET details=details-'participants' WHERE id=?",id);}
 @Transactional public Map<String,Object> create(CustomerRecordInput input,Authentication auth){var p=params(input,null,auth);var key=new GeneratedKeyHolder();named.update("INSERT INTO penny_customer_records(customer_id,site_id,kind,name,product_type_id,sale_id,starts_on,expires_on,status,notes,details) VALUES (:customer,:site,:kind,:name,:type,:sale,:starts,:expires,:status,:notes,CAST(:details AS jsonb))",p,key,new String[]{"id"});long id=Objects.requireNonNull(key.getKey()).longValue();participants(id,input);audit.record(auth,"CUSTOMER_RECORD_CREATED","CUSTOMER_RECORD",id,Map.of("kind",input.kind()));return Map.of("id",id);}
 @Transactional public Map<String,Object> update(long id,CustomerRecordInput input,Authentication auth){var current=row(id,true);require(auth,permission((String)current.get("kind"))+"_WRITE");var p=params(input,current,auth).addValue("id",id);named.update("UPDATE penny_customer_records SET customer_id=:customer,site_id=:site,name=:name,product_type_id=:type,sale_id=:sale,starts_on=:starts,expires_on=:expires,status=:status,notes=:notes,details=CAST(:details AS jsonb),updated_at=now() WHERE id=:id",p);participants(id,input);audit.record(auth,"CUSTOMER_RECORD_UPDATED","CUSTOMER_RECORD",id,Map.of("kind",input.kind()));return Map.of("updated",true);}
 public Map<String,Object> deadlines(LocalDate from,LocalDate to,Authentication auth){require(auth,"CUSTOMERS_READ");if(from==null||to==null||to.isBefore(from)||to.isAfter(from.plusYears(2)))throw bad("Intervallo non valido");var user=user(auth);var kinds=new ArrayList<String>();for(String kind:List.of("TRAINING","CONTRACT","DEVELOPMENT","TECHNICAL","COMMERCIAL"))if(user!=null&&rbac.allowed(user.id,permission(kind)+"_READ"))kinds.add(kind);if(kinds.isEmpty())return Map.of("items",List.of());var p=new MapSqlParameterSource("kinds",kinds).addValue("from",from).addValue("to",to);return Map.of("items",named.queryForList("SELECT r.id AS \"recordId\",r.customer_id AS \"customerId\",c.name AS \"customerName\",r.name AS \"productName\",r.kind AS type,r.expires_on AS date FROM penny_customer_records r JOIN penny_customers c ON c.id=r.customer_id WHERE r.kind IN (:kinds) AND r.deleted_at IS NULL AND c.deleted_at IS NULL AND r.status<>'CANCELLED' AND r.expires_on BETWEEN :from AND :to UNION ALL SELECT r.id,r.customer_id,c.name,r.name,'TRAINING_START',r.starts_on FROM penny_customer_records r JOIN penny_customers c ON c.id=r.customer_id WHERE r.kind='TRAINING' AND r.kind IN (:kinds) AND r.deleted_at IS NULL AND c.deleted_at IS NULL AND r.status<>'CANCELLED' AND r.starts_on BETWEEN :from AND :to ORDER BY date,\"productName\"",p));}
}
