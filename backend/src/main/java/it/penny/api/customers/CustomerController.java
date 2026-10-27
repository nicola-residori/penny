package it.penny.api.customers;

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
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
 private final ContactController contacts;
 private final JdbcTemplate db;
 private final NamedParameterJdbcTemplate named;
 private final PennyUserRepository users;
 private final RbacService rbac;
 private final it.penny.api.trash.TrashService trash;

 public CustomerController(JdbcTemplate db, PennyUserRepository users, RbacService rbac,it.penny.api.trash.TrashService trash,ContactController contacts) {
  this.contacts=contacts;this.db=db; this.named=new NamedParameterJdbcTemplate(db); this.users=users; this.rbac=rbac;this.trash=trash;
 }
 private void require(Authentication auth,String permission) {
  PennyUser user=auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null);
  rbac.require(user,permission);
 }
 private ResponseStatusException bad(String reason) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
 private String clean(String s,int max) {if(s==null)return null;String v=s.trim();if(v.length()>max)throw bad("Campo troppo lungo (max "+max+")");return v.isEmpty()?null:v;}
 private String required(String s,int max) {String v=clean(s,max);if(v==null)throw bad("Campo obbligatorio");return v;}
 private void email(String s) {if(s!=null&&!s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw bad("Email non valida");}
 private void country(String s) {if(s!=null&&!s.matches("[A-Z]{2}"))throw bad("Codice paese ISO non valido");}
 private void kind(String s) {if(!"COMPANY".equals(s)&&!"PERSON".equals(s))throw bad("Tipo cliente non valido");}
 private void exists(long id) {Integer n=db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=? AND deleted_at IS NULL",Integer.class,id);if(n==null||n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente non trovato");}
 public record CustomerInput(String kind,String name,String vatNumber,String taxCode,String email,String phone,String street,String postalCode,String city,String province,String country,String notes,String lifecycleStatus) {}
 public record ContactInput(String fullName,String role,String email,String phone,Boolean isPrimary,Long siteId) {}
 private MapSqlParameterSource params(CustomerInput input) {
  if(input==null)throw bad("Dati obbligatori");kind(input.kind());if(input.lifecycleStatus()!=null)throw bad("Lo stato commerciale è automatico alla prima vendita");String name=required(input.name(),200);
  String vat=clean(input.vatNumber(),32),tax=clean(input.taxCode(),32),mail=clean(input.email(),254);
  email(mail);String nation=clean(input.country(),2);if(nation==null)nation="IT";country(nation);
  String notes=input.notes()==null?null:input.notes().trim();if(notes!=null&&notes.length()>10000)throw bad("Note troppo lunghe");
  return new MapSqlParameterSource().addValue("kind",input.kind()).addValue("name",name).addValue("vat",vat)
   .addValue("tax",tax).addValue("email",mail).addValue("phone",clean(input.phone(),40))
   .addValue("street",clean(input.street(),240)).addValue("postal",clean(input.postalCode(),20))
   .addValue("city",clean(input.city(),120)).addValue("province",clean(input.province(),100))
   .addValue("country",nation).addValue("notes",notes).addValue("lifecycle",input.lifecycleStatus());
 }
 private static final Map<String,String> BUSINESS=Map.ofEntries(
 Map.entry("sector","sector"),Map.entry("website","website"),Map.entry("pec","pec"),Map.entry("sdiCode","sdi_code"),Map.entry("defaultPayment","default_payment"),Map.entry("bankDetails1","bank_details1"),Map.entry("bankDetails2","bank_details2"),Map.entry("administrativeNotes","administrative_notes"),Map.entry("commercialContactId","commercial_contact_id"),Map.entry("administrativeContactId","administrative_contact_id"),Map.entry("isInsolvent","is_insolvent"),Map.entry("isCollaborator","is_collaborator"),Map.entry("companyStatus","company_status"),Map.entry("replacementCustomerId","replacement_customer_id"),Map.entry("previousCustomerId","previous_customer_id"));
 private CustomerInput legacy(JsonNode input){try{return new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false).treeToValue(input,CustomerInput.class);}catch(Exception ex){throw bad("Dati cliente non validi");}}
 private void business(long id,JsonNode input){
  var p=new MapSqlParameterSource().addValue("id",id);var updates=new ArrayList<String>();
  for(var entry:BUSINESS.entrySet())if(input.has(entry.getKey())){
   String key=entry.getKey();JsonNode node=input.get(key);Object value=null;
   if(key.startsWith("is")){if(!node.isBoolean())throw bad("Valore booleano richiesto: "+key);value=node.booleanValue();}
   else if(key.endsWith("Id")){if(!node.isNull()&&(!node.isIntegralNumber()||!node.canConvertToLong()||node.longValue()<1))throw bad("Riferimento non valido");value=node.isNull()?null:node.longValue();}
   else {if(!node.isNull()&&!node.isTextual())throw bad("Testo richiesto: "+key);int max=switch(key){case "sector"->200;case "website","defaultPayment"->500;case "pec"->254;case "sdiCode"->40;case "companyStatus"->20;default->10000;};value=clean(node.isNull()?null:node.textValue(),max);if(key.equals("pec"))email((String)value);if(key.equals("companyStatus")&&(value==null||!Set.of("ACTIVE","OBSOLETE","REPLACED","CEASED").contains(value)))throw bad("Stato azienda non valido");}
   updates.add(entry.getValue()+"=:"+key);p.addValue(key,value);
  }
  if(!updates.isEmpty())named.update("UPDATE penny_customers SET "+String.join(",",updates)+" WHERE id=:id",p);
 }
 private static final String FIELDS="(SELECT string_agg(concat_ws(', ',s.street,s.city), ' · ' ORDER BY s.name,s.id) FROM penny_customer_sites s WHERE s.customer_id=penny_customers.id AND jsonb_exists(s.site_types,'OPERATIONAL')) AS \"operationalAddress\",(SELECT string_agg(DISTINCT s.province, ' · ') FROM penny_customer_sites s WHERE s.customer_id=penny_customers.id AND jsonb_exists(s.site_types,'OPERATIONAL')) AS \"operationalProvince\","+BUSINESS.entrySet().stream().map(e->e.getValue()+" AS \""+e.getKey()+"\"").collect(java.util.stream.Collectors.joining(","))+",id,lifecycle_status AS \"lifecycleStatus\",kind,name,vat_number AS \"vatNumber\",tax_code AS \"taxCode\",email,phone,street,postal_code AS \"postalCode\",city,province,country,notes,archived,created_at AS \"createdAt\",updated_at AS \"updatedAt\"";
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="false") boolean archived,@RequestParam(required=false) String lifecycleStatus,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="asc") String direction,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth) {
  require(auth,"CUSTOMERS_READ");String order=Map.of("name","lower(name)","email","email","city","city","createdAt","created_at","companyStatus","company_status","sector","sector").get(sort);if(order==null||!Set.of("asc","desc").contains(direction))throw bad("Ordinamento non valido");if(page<0||page>100000||size<1||size>100)throw bad("Paginazione non valida");
  if(lifecycleStatus!=null)lifecycle(lifecycleStatus);String search=q.trim();if(search.length()>100)throw bad("Ricerca troppo lunga");String like="%"+search.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  String where=" WHERE deleted_at IS NULL AND archived=? AND (CAST(? AS varchar) IS NULL OR lifecycle_status=?) AND (lower(name) LIKE ? ESCAPE '\\' OR lower(coalesce(vat_number,'')) LIKE ? ESCAPE '\\' OR lower(coalesce(tax_code,'')) LIKE ? ESCAPE '\\')";
  Long total=db.queryForObject("SELECT count(*) FROM penny_customers"+where,Long.class,archived,lifecycleStatus,lifecycleStatus,like,like,like);
  var items=db.queryForList("SELECT "+FIELDS+" FROM penny_customers"+where+" ORDER BY "+order+" "+direction+" NULLS LAST,id LIMIT ? OFFSET ?",archived,lifecycleStatus,lifecycleStatus,like,like,like,size,page*size);
  return Map.of("items",items,"total",total==null?0:total,"page",page,"size",size);
 }
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable long id,Authentication auth){require(auth,"CUSTOMERS_READ");exists(id);return db.queryForMap("SELECT "+FIELDS+" FROM penny_customers WHERE id=? AND deleted_at IS NULL",id);}
 @PostMapping @Transactional public Map<String,Object> create(@RequestBody JsonNode body,Authentication auth) {
  require(auth,"CUSTOMERS_WRITE");var p=params(legacy(body));GeneratedKeyHolder key=new GeneratedKeyHolder();
  try {named.update("INSERT INTO penny_customers(kind,name,vat_number,tax_code,email,phone,street,postal_code,city,province,country,notes,lifecycle_status) VALUES (:kind,:name,:vat,:tax,:email,:phone,:street,:postal,:city,:province,:country,:notes,coalesce(:lifecycle,'PROSPECT'))",p,key,new String[]{"id"});}
  catch(DataIntegrityViolationException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Cliente duplicato o dati non validi");}
  long id=Objects.requireNonNull(key.getKey()).longValue();business(id,body);return Map.of("id",id);
 }
 @PutMapping("/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,@RequestBody JsonNode body,Authentication auth) {
  require(auth,"CUSTOMERS_WRITE");exists(id);var p=params(legacy(body)).addValue("id",id);
  try {named.update("UPDATE penny_customers SET kind=:kind,name=:name,vat_number=:vat,tax_code=:tax,email=:email,phone=:phone,street=:street,postal_code=:postal,city=:city,province=:province,country=:country,notes=:notes,lifecycle_status=coalesce(:lifecycle,lifecycle_status),updated_at=now() WHERE id=:id AND deleted_at IS NULL",p);}
  catch(DataIntegrityViolationException ex){throw new ResponseStatusException(HttpStatus.CONFLICT,"Cliente duplicato o dati non validi");}
  business(id,body);return Map.of("updated",true);
 }
 private void lifecycle(String value){if(!"PROSPECT".equals(value)&&!"CUSTOMER".equals(value))throw bad("Stato commerciale non valido");}
 @PatchMapping("/{id}/archive") @Transactional public Map<String,Object> archive(@PathVariable long id,@RequestBody Map<String,Boolean> body,Authentication auth) {
  require(auth,"CUSTOMERS_WRITE");exists(id);if(body.get("archived")==null)throw bad("archived obbligatorio");db.update("UPDATE penny_customers SET archived=?,updated_at=now() WHERE id=? AND deleted_at IS NULL",body.get("archived"),id);return Map.of("updated",true);
 }
 @GetMapping("/{id}/contacts") public List<Map<String,Object>> contacts(@PathVariable long id,Authentication auth) {
  require(auth,"CUSTOMERS_READ");exists(id);return db.queryForList("SELECT c.id,c.id AS \"contactId\",l.customer_id AS \"customerId\",c.full_name AS \"fullName\",l.role,c.email,c.phone,l.is_primary AS \"isPrimary\",l.site_id AS \"siteId\",s.name AS \"siteName\",(SELECT count(*) FROM penny_contact_customers x JOIN penny_customers cu ON cu.id=x.customer_id WHERE x.contact_id=c.id AND cu.deleted_at IS NULL) AS \"customerCount\" FROM penny_customer_contacts c JOIN penny_contact_customers l ON l.contact_id=c.id LEFT JOIN penny_customer_sites s ON s.id=l.site_id WHERE l.customer_id=? AND c.deleted_at IS NULL ORDER BY l.is_primary DESC,c.full_name,c.id",id);
 }
 @PostMapping("/{id}/contacts") @Transactional public Map<String,Object> addContact(@PathVariable long id,@RequestBody ContactInput input,Authentication auth) {
  require(auth,"CUSTOMERS_WRITE");exists(id);
  return contacts.create(new ContactController.ContactInput(input.fullName(),input.email(),input.phone(),List.of(new ContactController.LinkInput(id,input.siteId(),input.role(),input.isPrimary()))),auth);
 }
 @DeleteMapping("/{id}/contacts/{contactId}") @Transactional public Map<String,Object> deleteContact(@PathVariable long id,@PathVariable long contactId,Authentication auth) {
  require(auth,"CUSTOMERS_WRITE");exists(id);return contacts.unlinkContact(contactId,id);
 }
}
