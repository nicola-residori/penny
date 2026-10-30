package it.penny.api.customers;

import it.penny.api.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/contacts")
public class ContactController {
 private final ObjectMapper json; private final JdbcTemplate db; private final PennyUserRepository users; private final RbacService rbac;
 public ContactController(JdbcTemplate db,PennyUserRepository users,RbacService rbac,ObjectMapper json){this.json=json;this.db=db;this.users=users;this.rbac=rbac;}
 private void require(Authentication auth,String permission){rbac.require(auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null),permission);}
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private String clean(String value,int max){if(value==null)return null;value=value.trim();if(value.length()>max)throw bad("Campo troppo lungo");return value.isEmpty()?null:value;}
 private String name(String value){value=clean(value,200);if(value==null)throw bad("Nome obbligatorio");return value;}
 private String email(String value){value=clean(value,254);if(value!=null&&!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw bad("Email non valida");return value;}
 public record LinkInput(Long customerId,Long siteId,String role,Boolean isPrimary){}
 public record ContactInput(String fullName,String email,String phone,List<LinkInput> links,com.fasterxml.jackson.databind.JsonNode firstName,com.fasterxml.jackson.databind.JsonNode lastName,com.fasterxml.jackson.databind.JsonNode companyMobile,com.fasterxml.jackson.databind.JsonNode personalMobile,com.fasterxml.jackson.databind.JsonNode contactStatus,com.fasterxml.jackson.databind.JsonNode contactReference){
  public ContactInput(String fullName,String email,String phone,List<LinkInput> links){this(fullName,email,phone,links,null,null,null,null,null,null);}
 }
 static final String EXTRA_FIELDS="c.first_name AS \"firstName\",c.last_name AS \"lastName\",c.company_mobile AS \"companyMobile\",c.personal_mobile AS \"personalMobile\",c.contact_status AS \"contactStatus\",c.contact_reference AS \"contactReference\",";
 private String contactName(ContactInput input){
  if(input.fullName()!=null&&!input.fullName().isBlank())return name(input.fullName());
  String first=input.firstName()!=null&&input.firstName().isTextual()?input.firstName().textValue():"";
  String last=input.lastName()!=null&&input.lastName().isTextual()?input.lastName().textValue():"";
  return name((first+" "+last).trim());
 }
 private void saveDetails(long id,ContactInput input){
  var values=new LinkedHashMap<String,com.fasterxml.jackson.databind.JsonNode>();values.put("first_name",input.firstName());values.put("last_name",input.lastName());values.put("company_mobile",input.companyMobile());values.put("personal_mobile",input.personalMobile());values.put("contact_status",input.contactStatus());values.put("contact_reference",input.contactReference());
  for(var entry:values.entrySet()){var node=entry.getValue();if(node==null)continue;if(!node.isNull()&&!node.isTextual())throw bad("Campo contatto non valido");String value=node.isNull()?null:clean(node.textValue(),entry.getKey().contains("mobile")?40:entry.getKey().startsWith("contact_")?120:200);if(entry.getKey().equals("contact_status")&&value==null)value="ACTIVE";db.update("UPDATE penny_customer_contacts SET "+entry.getKey()+"=? WHERE id=?",value,id);}
  if(input.firstName()!=null||input.lastName()!=null){String structured=db.queryForObject("SELECT trim(concat_ws(' ',first_name,last_name)) FROM penny_customer_contacts WHERE id=?",String.class,id);if(structured!=null&&structured.length()>200)throw bad("Nome e cognome troppo lunghi");db.update("UPDATE penny_customer_contacts SET full_name=coalesce(nullif(trim(concat_ws(' ',first_name,last_name)),''),full_name) WHERE id=?",id);}
 }

 private static final String FIELDS=EXTRA_FIELDS+"(c.image_data IS NOT NULL) AS \"hasImage\",c.id,c.full_name AS \"fullName\",c.email,c.phone,(SELECT count(*) FROM penny_contact_customers cc JOIN penny_customers cu ON cu.id=cc.customer_id WHERE cc.contact_id=c.id AND cu.deleted_at IS NULL) AS \"customerCount\"";
 private static final String LIVE="c.deleted_at IS NULL AND EXISTS(SELECT 1 FROM penny_contact_customers l JOIN penny_customers cu ON cu.id=l.customer_id WHERE l.contact_id=c.id AND cu.deleted_at IS NULL)";
 private void lock(long id){if(db.queryForList("SELECT c.id FROM penny_customer_contacts c WHERE c.id=? AND "+LIVE+" FOR UPDATE OF c",id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Contatto non trovato");}
 private void validateLinks(List<LinkInput> links){
  if(links==null||links.isEmpty())throw bad("Il contatto richiede almeno un cliente o prospect");
  Set<Long> seen=new HashSet<>();for(var link:links){if(link==null||link.customerId()==null||!seen.add(link.customerId()))throw bad("Clienti mancanti o duplicati");validateLink(link);}
 }
 private void validateLink(LinkInput link){
  if(link.customerId()==null||db.queryForList("SELECT id FROM penny_customers WHERE id=? AND deleted_at IS NULL FOR SHARE",link.customerId()).isEmpty())throw bad("Cliente non disponibile");
  if(link.siteId()!=null&&db.queryForList("SELECT id FROM penny_customer_sites WHERE id=? AND customer_id=? FOR SHARE",link.siteId(),link.customerId()).isEmpty())throw bad("La sede deve appartenere al cliente selezionato");clean(link.role(),120);
 }
 private void saveLink(long id,LinkInput link){
  if(Boolean.TRUE.equals(link.isPrimary()))db.update("UPDATE penny_contact_customers SET is_primary=false WHERE customer_id=? AND contact_id<>? AND contact_id IN (SELECT id FROM penny_customer_contacts WHERE deleted_at IS NULL)",link.customerId(),id);
  db.update("INSERT INTO penny_contact_customers(contact_id,customer_id,site_id,role,is_primary) VALUES (?,?,?,?,?) ON CONFLICT(contact_id,customer_id) DO UPDATE SET site_id=excluded.site_id,role=excluded.role,is_primary=excluded.is_primary",id,link.customerId(),link.siteId(),clean(link.role(),120),Boolean.TRUE.equals(link.isPrimary()));
 }
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="asc") String direction,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
  require(auth,"CUSTOMERS_READ");String order=Map.of("name","lower(c.full_name)","email","c.email","phone","c.phone").get(sort);if(order==null||!Set.of("asc","desc").contains(direction))throw bad("Ordinamento non valido");if(page<0||page>100000||size<1||size>100||q.length()>100)throw bad("Ricerca o paginazione non valida");
  String like="%"+q.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  String where=" WHERE "+LIVE+" AND (lower(c.full_name) LIKE ? ESCAPE '\\' OR lower(coalesce(c.email,'')) LIKE ? ESCAPE '\\' OR lower(coalesce(c.phone,'')) LIKE ? ESCAPE '\\' OR EXISTS(SELECT 1 FROM penny_contact_customers l JOIN penny_customers cu ON cu.id=l.customer_id WHERE l.contact_id=c.id AND cu.deleted_at IS NULL AND lower(cu.name) LIKE ? ESCAPE '\\'))";
  long total=Objects.requireNonNull(db.queryForObject("SELECT count(*) FROM penny_customer_contacts c"+where,Long.class,like,like,like,like));
  var items=db.queryForList("SELECT "+FIELDS+" FROM penny_customer_contacts c"+where+" ORDER BY "+order+" "+direction+" NULLS LAST,c.id LIMIT ? OFFSET ?",like,like,like,like,size,page*size);
  for(var item:items)item.put("links",links(((Number)item.get("id")).longValue()));
  return Map.of("items",items,"total",total,"page",page,"size",size);
 }
 private List<Map<String,Object>> links(long id){var rows=db.queryForList("SELECT l.customer_id AS \"customerId\",cu.name AS \"customerName\",cu.lifecycle_status AS \"lifecycleStatus\",l.site_id AS \"siteId\",s.name AS \"siteName\",coalesce(s.site_types,'[]'::jsonb)::text AS \"siteTypes\",cu.archived,l.role,l.is_primary AS \"isPrimary\" FROM penny_contact_customers l JOIN penny_customers cu ON cu.id=l.customer_id LEFT JOIN penny_customer_sites s ON s.id=l.site_id WHERE l.contact_id=? AND cu.deleted_at IS NULL ORDER BY cu.name,cu.id",id);for(var row:rows){try{row.put("siteTypes",json.readValue((String)row.get("siteTypes"),json.getTypeFactory().constructCollectionType(List.class,String.class)));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid stored site types",e);}}return rows;}
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable long id,Authentication auth){require(auth,"CUSTOMERS_READ");var rows=db.queryForList("SELECT "+FIELDS+" FROM penny_customer_contacts c WHERE c.id=? AND "+LIVE,id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Contatto non trovato");var result=rows.getFirst();result.put("links",links(id));return result;}
 @PostMapping @Transactional public Map<String,Object> create(@RequestBody ContactInput input,Authentication auth){
  require(auth,"CUSTOMERS_WRITE");String name=contactName(input),mail=email(input.email()),phone=clean(input.phone(),40);validateLinks(input.links());var first=input.links().getFirst();
  long id=Objects.requireNonNull(db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name,email,phone) VALUES (?,?,?,?) RETURNING id",Long.class,first.customerId(),name,mail,phone));
  for(var link:input.links())saveLink(id,link);saveDetails(id,input);return Map.of("id",id);
 }
 @PutMapping("/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,@RequestBody ContactInput input,Authentication auth){
  require(auth,"CUSTOMERS_WRITE");lock(id);String name=contactName(input),mail=email(input.email()),phone=clean(input.phone(),40);validateLinks(input.links());
  var retained=input.links().stream().map(LinkInput::customerId).toList();
  for(var ref:db.queryForList("SELECT id FROM penny_customers WHERE commercial_contact_id=? OR administrative_contact_id=?",id,id))if(!retained.contains(((Number)ref.get("id")).longValue()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Rimuovere prima il contatto dai referenti commerciali o amministrativi");
  // Preserve relationships to trashed customers: they are deliberately hidden from the editor until restored.
  db.update("DELETE FROM penny_contact_customers l USING penny_customers cu WHERE l.customer_id=cu.id AND cu.deleted_at IS NULL AND l.contact_id=?",id);
  for(var link:input.links())saveLink(id,link);
  db.update("UPDATE penny_customer_contacts SET customer_id=?,full_name=?,email=?,phone=? WHERE id=?",input.links().getFirst().customerId(),name,mail,phone,id);saveDetails(id,input);return Map.of("updated",true);
 }
 @PutMapping("/{id}/customers/{customerId}") @Transactional public Map<String,Object> link(@PathVariable long id,@PathVariable long customerId,@RequestBody LinkInput input,Authentication auth){require(auth,"CUSTOMERS_WRITE");lock(id);var link=new LinkInput(customerId,input.siteId(),input.role(),input.isPrimary());validateLink(link);saveLink(id,link);return Map.of("updated",true);}
 @DeleteMapping("/{id}/customers/{customerId}") @Transactional public Map<String,Object> unlink(@PathVariable long id,@PathVariable long customerId,Authentication auth){require(auth,"CUSTOMERS_WRITE");return unlinkContact(id,customerId);}
 @Transactional public Map<String,Object> unlinkContact(long id,long customerId){
  lock(id);if(!db.queryForList("SELECT id FROM penny_customers WHERE id=? AND (commercial_contact_id=? OR administrative_contact_id=?)",customerId,id,id).isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Rimuovere prima il contatto dai referenti commerciali o amministrativi");if(db.queryForObject("SELECT count(*) FROM penny_contact_customers WHERE contact_id=? AND customer_id=?",Integer.class,id,customerId)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Associazione non trovata");
  if(db.queryForObject("SELECT count(*) FROM penny_contact_customers WHERE contact_id=?",Integer.class,id)<=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Il contatto deve restare associato ad almeno un cliente o prospect; usa il cestino per eliminarlo");
  if(db.queryForObject("SELECT count(*) FROM penny_contact_customers l JOIN penny_customers cu ON cu.id=l.customer_id WHERE l.contact_id=? AND l.customer_id<>? AND cu.deleted_at IS NULL",Integer.class,id,customerId)==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Il contatto deve restare associato ad almeno un cliente o prospect attivo");
  Long anchor=db.queryForObject("SELECT customer_id FROM penny_contact_customers WHERE contact_id=? AND customer_id<>? ORDER BY customer_id LIMIT 1",Long.class,id,customerId);
  db.update("UPDATE penny_customer_contacts SET customer_id=? WHERE id=? AND customer_id=?",anchor,id,customerId);
  db.update("DELETE FROM penny_contact_customers WHERE contact_id=? AND customer_id=?",id,customerId);return Map.of("deleted",true);
 }
}
