package it.penny.api.trash;

import it.penny.api.security.*;
import it.penny.api.common.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

@Service
public class TrashService {
 private final JdbcTemplate db;private final PennyUserRepository users;private final RbacService rbac;private final AuditLog audit;private final TransactionTemplate tx;
 public TrashService(JdbcTemplate db,PennyUserRepository users,RbacService rbac,AuditLog audit,PlatformTransactionManager transactions){this.db=db;this.users=users;this.rbac=rbac;this.audit=audit;this.tx=new TransactionTemplate(transactions);}
 private PennyUser user(Authentication auth){return auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null);}
 private void require(Authentication auth,String... permissions){var user=user(auth);for(String permission:permissions)rbac.require(user,permission);}
 private boolean allowed(PennyUser user,String permission){return user!=null&&user.enabled&&!user.mustChangePassword&&rbac.allowed(user.id,permission);}
 private String permission(TrashEntity type,long id){if(type!=TrashEntity.CUSTOMER_RECORD)return type.permission;var kinds=db.queryForList("SELECT kind FROM penny_customer_records WHERE id=?",String.class,id);if(kinds.isEmpty())throw missing();return it.penny.api.records.CustomerRecordService.permission(kinds.getFirst());}
 private boolean allowedType(PennyUser user,TrashEntity type,String suffix){return type==TrashEntity.CUSTOMER_RECORD?(allowed(user,"TRAINING_"+suffix)||allowed(user,"CONTRACTS_"+suffix)||allowed(user,"ACTIVITIES_"+suffix)):allowed(user,type.permission+"_"+suffix);}
 private String recordScope(PennyUser user,boolean write){var kinds=new ArrayList<String>();for(String kind:List.of("TRAINING","CONTRACT","DEVELOPMENT","TECHNICAL","COMMERCIAL")){String permission=it.penny.api.records.CustomerRecordService.permission(kind);if(allowed(user,permission+"_READ")&&(!write||allowed(user,permission+"_WRITE")))kinds.add("'"+kind+"'");}return " AND (entity_type<>'CUSTOMER_RECORD' OR entity_id IN (SELECT id FROM penny_customer_records WHERE kind IN ("+(kinds.isEmpty()?"'NONE'":String.join(",",kinds))+")))";}
 private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Elemento non trovato");}
 private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
 private Timestamp now(){return db.queryForObject("SELECT clock_timestamp()",Timestamp.class);}
 private Map<String,Object> target(TrashEntity type,long id,boolean lock){var rows=db.queryForList("SELECT * FROM "+type.table+" WHERE id=?"+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw missing();return rows.getFirst();}
 @Transactional public Map<String,Object> move(String entityType,long id,Authentication auth){
  TrashEntity type=TrashEntity.parse(entityType);require(auth,"TRASH_DELETE",permission(type,id)+"_READ",permission(type,id)+"_WRITE");var row=target(type,id,true);if(row.get("deleted_at")!=null)throw conflict("Elemento già nel cestino");
  // Database triggers protect the same invariant against concurrent writes and direct SQL.
  String dependencies=switch(type){case CUSTOMER->"SELECT (SELECT count(*) FROM penny_sales WHERE customer_id=?)+(SELECT count(*) FROM penny_installed_products WHERE customer_id=?)+(SELECT count(*) FROM penny_workstations WHERE customer_id=?)+(SELECT count(*) FROM penny_customer_records WHERE customer_id=?)";case CATALOG_PRODUCT->"SELECT (SELECT count(*) FROM penny_sales WHERE catalog_product_id=?)+(SELECT count(*) FROM penny_installed_products WHERE catalog_product_id=?)";case INSTALLED_PRODUCT->"SELECT count(*) FROM penny_sale_allocations WHERE installed_product_id=?";case WORKSTATION->"SELECT count(*) FROM penny_installed_products WHERE workstation_id=?";case CONTACT,CUSTOMER_RECORD->null;};
  if(dependencies!=null){Object[] params=Collections.nCopies((int)dependencies.chars().filter(c->c=='?').count(),id).toArray();if(db.queryForObject(dependencies,Long.class,params)>0)throw conflict("Elemento collegato a vendite, installazioni, postazioni o assegnazioni. Conserva lo storico usando Archivia oppure rimuovi prima i collegamenti.");}
  if(type==TrashEntity.CONTACT&&db.queryForList("SELECT cu.id FROM penny_customers cu JOIN penny_contact_customers l ON l.customer_id=cu.id WHERE l.contact_id=? AND cu.deleted_at IS NULL FOR SHARE OF cu",id).isEmpty())throw conflict("Ripristina prima un cliente associato al contatto");
  Timestamp deleted=now(),expires=Timestamp.from(deleted.toInstant().plus(Duration.ofDays(30)));
  db.update("UPDATE "+type.table+" SET deleted_at=? WHERE id=?",deleted,id);
  db.update("INSERT INTO penny_trash_entries(entity_type,entity_id,label,deleted_by,deleted_at,expires_at) VALUES (?,?,?,?,?,?)",type.name(),id,row.get(type.label),auth.getName(),deleted,expires);
  audit.record(auth,"TRASH_MOVED",type.name(),id,Map.of("expiresAt",expires.toInstant().toString()));return Map.of("trashed",true,"expiresAt",expires.toInstant().toString());
 }
 public Map<String,Object> list(String q,int page,int size,Authentication auth){
  require(auth,"TRASH_READ");if(page<0||page>100000||size<1||size>100||q.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ricerca o paginazione non valida");
  var user=user(auth);var types=new ArrayList<String>();for(var type:TrashEntity.values())if(allowedType(user,type,"READ"))types.add(type.name());
  boolean canEmpty=allowed(user,"TRASH_PURGE");var purgeTypes=purgeTypes(user);
  long purgeableTotal=purgeTypes.isEmpty()?0:new NamedParameterJdbcTemplate(db).queryForObject("SELECT count(*) FROM penny_trash_entries WHERE entity_type IN (:types)"+recordScope(user,true),new MapSqlParameterSource("types",purgeTypes),Long.class);
  if(types.isEmpty())return Map.of("items",List.of(),"total",0,"page",page,"size",size,"canEmpty",canEmpty,"purgeableTotal",purgeableTotal);
  String like="%"+q.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  var params=new MapSqlParameterSource().addValue("types",types).addValue("q",like).addValue("size",size).addValue("offset",page*size);
  var named=new NamedParameterJdbcTemplate(db);var kinds=new ArrayList<String>();for(String kind:List.of("TRAINING","CONTRACT","DEVELOPMENT","TECHNICAL","COMMERCIAL"))if(allowed(user,it.penny.api.records.CustomerRecordService.permission(kind)+"_READ"))kinds.add(kind);params.addValue("kinds",kinds.isEmpty()?List.of("NONE"):kinds);
  String where=" WHERE entity_type IN (:types) AND (entity_type<>'CUSTOMER_RECORD' OR entity_id IN (SELECT id FROM penny_customer_records WHERE kind IN (:kinds))) AND lower(label) LIKE :q ESCAPE '\\'";
  var items=named.queryForList("SELECT id,entity_type AS \"entityType\",entity_id AS \"entityId\",label,deleted_at AS \"deletedAt\",expires_at AS \"expiresAt\",blocked_reason AS \"blockedReason\",greatest(0,ceil(extract(epoch FROM (expires_at-clock_timestamp()))/86400))::integer AS \"daysRemaining\",expires_at<=clock_timestamp() AS expired FROM penny_trash_entries"+where+" ORDER BY deleted_at DESC,id DESC LIMIT :size OFFSET :offset",params);
  for(var item:items){var type=TrashEntity.parse((String)item.get("entityType"));item.put("canRestore",!Boolean.TRUE.equals(item.get("expired"))&&allowed(user,"TRASH_RESTORE")&&allowed(user,permission(type,((Number)item.get("entityId")).longValue())+"_WRITE"));item.put("deletedAt",((Timestamp)item.get("deletedAt")).toInstant().toString());item.put("expiresAt",((Timestamp)item.get("expiresAt")).toInstant().toString());}
  return Map.of("items",items,"total",Objects.requireNonNull(named.queryForObject("SELECT count(*) FROM penny_trash_entries"+where,params,Long.class)),"page",page,"size",size,"canEmpty",canEmpty,"purgeableTotal",purgeableTotal);
 }
 @Transactional public Map<String,Object> restore(long entryId,Authentication auth){
  require(auth,"TRASH_RESTORE");var rows=db.queryForList("SELECT * FROM penny_trash_entries WHERE id=? FOR UPDATE",entryId);if(rows.isEmpty())throw missing();var entry=rows.getFirst();var type=TrashEntity.parse((String)entry.get("entity_type"));
  long entityId=((Number)entry.get("entity_id")).longValue();require(auth,permission(type,entityId)+"_READ",permission(type,entityId)+"_WRITE");if(!((Timestamp)entry.get("expires_at")).after(now()))throw new ResponseStatusException(HttpStatus.GONE,"I 30 giorni sono scaduti: ripristino non disponibile");
  long id=((Number)entry.get("entity_id")).longValue();var row=target(type,id,true);if(row.get("deleted_at")==null)throw conflict("Elemento non più nel cestino");
  db.update("UPDATE "+type.table+" SET deleted_at=NULL WHERE id=?",id);db.update("DELETE FROM penny_trash_entries WHERE id=?",entryId);
  audit.record(auth,"TRASH_RESTORED",type.name(),id,Map.of());return Map.of("restored",true);
 }
 private static final String PURGE_ORDER=" ORDER BY CASE entity_type WHEN 'CONTACT' THEN 0 WHEN 'INSTALLED_PRODUCT' THEN 1 WHEN 'WORKSTATION' THEN 2 WHEN 'CATALOG_PRODUCT' THEN 3 ELSE 4 END,id";
 private List<String> purgeTypes(PennyUser user){
  var types=new ArrayList<String>();
  if(allowed(user,"TRASH_PURGE"))for(var type:TrashEntity.values())if(allowedType(user,type,"READ")&&allowedType(user,type,"WRITE"))types.add(type.name());
  return types;
 }
 public Map<String,Integer> empty(boolean confirm,Authentication auth){
  require(auth,"TRASH_READ","TRASH_PURGE");
  if(!confirm)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Conferma esplicita richiesta per la rimozione definitiva");
  var types=purgeTypes(user(auth));
  // Snapshot all authorized entries once: search, pagination, expiry and retry cooldown do not restrict manual emptying.
  var ids=types.isEmpty()?List.<Long>of():new NamedParameterJdbcTemplate(db).queryForList("SELECT id FROM penny_trash_entries WHERE entity_type IN (:types)"+recordScope(user(auth),true)+PURGE_ORDER,new MapSqlParameterSource("types",types),Long.class);
  int purged=0,blocked=0,skipped=0;
  for(long entryId:ids){String result=purgeEntry(entryId,false,auth);if("PURGED".equals(result))purged++;else if("BLOCKED".equals(result))blocked++;else skipped++;}
  return Map.of("purged",purged,"blocked",blocked,"skipped",skipped);
 }
 private String purgeEntry(long entryId,boolean expiredOnly,Authentication actor){
  try{return tx.execute(status->{
   var rows=db.queryForList("SELECT * FROM penny_trash_entries WHERE id=?"+(expiredOnly?" AND expires_at<=clock_timestamp()":"")+" FOR UPDATE SKIP LOCKED",entryId);if(rows.isEmpty())return "SKIPPED";
   var entry=rows.getFirst();var type=TrashEntity.parse((String)entry.get("entity_type"));long id=((Number)entry.get("entity_id")).longValue();
   if(!expiredOnly){var user=user(actor);if(!allowed(user,"TRASH_READ")||!purgeTypes(user).contains(type.name())||!allowed(user,permission(type,id)+"_READ")||!allowed(user,permission(type,id)+"_WRITE"))return "SKIPPED";}
   var targetRows=db.queryForList("SELECT deleted_at FROM "+type.table+" WHERE id=? FOR UPDATE",id);
   if(!targetRows.isEmpty()&&targetRows.getFirst().get("deleted_at")==null){db.update("UPDATE penny_trash_entries SET blocked_reason=?,last_cleanup_at=clock_timestamp() WHERE id=?","Elemento ripristinato o modificato: verifica necessaria",entryId);return "BLOCKED";}
   if(type==TrashEntity.CUSTOMER && db.queryForObject("SELECT count(*) FROM penny_contact_customers l WHERE l.customer_id=? AND EXISTS(SELECT 1 FROM penny_contact_customers x WHERE x.contact_id=l.contact_id AND x.customer_id<>l.customer_id)",Long.class,id)>0){db.update("UPDATE penny_trash_entries SET blocked_reason=?,last_cleanup_at=clock_timestamp() WHERE id=?","Ripristina il cliente e rimuovi le associazioni ai contatti condivisi prima della rimozione definitiva",entryId);return "BLOCKED";}
   db.update("DELETE FROM "+type.table+" WHERE id=? AND deleted_at IS NOT NULL",id);db.update("DELETE FROM penny_trash_entries WHERE id=?",entryId);
   audit.record(actor,"TRASH_PURGED",type.name(),id,Map.of());return "PURGED";
  });}catch(DataIntegrityViolationException|org.springframework.dao.ConcurrencyFailureException e){
   tx.executeWithoutResult(status->db.update("UPDATE penny_trash_entries SET blocked_reason=?,last_cleanup_at=clock_timestamp() WHERE id=?","Rimozione rinviata: elemento collegato ad altri dati o modificato contemporaneamente",entryId));return "BLOCKED";
  }
 }
 public Map<String,Integer> purgeExpired(){
  int purged=0,blocked=0;var system=new UsernamePasswordAuthenticationToken("system:trash-retention",null);
  for(int batch=0;batch<100;batch++){
   var ids=db.queryForList("SELECT id FROM penny_trash_entries WHERE expires_at<=clock_timestamp() AND (last_cleanup_at IS NULL OR last_cleanup_at<clock_timestamp()-interval '55 minutes')"+PURGE_ORDER+" LIMIT 100",Long.class);
   if(ids.isEmpty())break;int processed=0;
   for(long entryId:ids){String result=purgeEntry(entryId,true,system);if("PURGED".equals(result)){purged++;processed++;}else if("BLOCKED".equals(result)){blocked++;processed++;}}
   if(processed==0)break;
  }
  return Map.of("purged",purged,"blocked",blocked);
 }
}
