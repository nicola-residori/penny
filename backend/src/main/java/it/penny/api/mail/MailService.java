package it.penny.api.mail;

import it.penny.api.security.*;
import it.penny.api.common.AuditLog;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.security.SecureRandom;

@Service
public class MailService {
 final JdbcTemplate db;final PennyUserRepository users;final RbacService rbac;final AuditLog audit;
 public MailService(JdbcTemplate db,PennyUserRepository users,RbacService rbac,AuditLog audit){this.db=db;this.users=users;this.rbac=rbac;this.audit=audit;}
 PennyUser require(Authentication a,boolean write){var u=a==null?null:users.findByEmailIgnoreCase(a.getName()).orElse(null);rbac.require(u,"CUSTOMERS_READ");rbac.require(u,write?"DOCUMENTS_WRITE":"DOCUMENTS_READ");return u;}
 Authentication bearer(String header){
  if(header==null||!header.startsWith("Bearer ")||header.length()>200)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Collega nuovamente Outlook a Penny");
  var rows=db.queryForList("SELECT u.email FROM penny_mail_connections t JOIN penny_users u ON u.id=t.user_id WHERE token_hash=? AND revoked_at IS NULL AND expires_at>now() AND t.session_version=u.session_version AND u.enabled AND NOT u.must_change_password",MailPayload.hash(header.substring(7)));
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Connessione Outlook scaduta o revocata");
  var a=new UsernamePasswordAuthenticationToken(rows.getFirst().get("email"),"outlook");require(a,true);return a;
 }
 @Transactional public Map<String,Object> connect(Authentication a){var u=require(a,true);byte[] random=new byte[32];new SecureRandom().nextBytes(random);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
  // Bounded number of live connections per user; old connections can be revoked in Penny.
  db.update("UPDATE penny_mail_connections SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL AND expires_at<=now()",u.id);
  if(db.queryForObject("SELECT count(*) FROM penny_mail_connections WHERE user_id=? AND revoked_at IS NULL",Long.class,u.id)>=20)throw new ResponseStatusException(HttpStatus.CONFLICT,"Revoca una vecchia connessione Outlook prima di aggiungerne un'altra");
  var row=db.queryForMap("INSERT INTO penny_mail_connections(user_id,token_hash,session_version) VALUES (?,?,?) RETURNING id,expires_at AS \"expiresAt\"",u.id,MailPayload.hash(token),u.sessionVersion);audit.record(a,"MAIL_CONNECTED","MAIL_CONNECTION",((Number)row.get("id")).longValue(),Map.of());return Map.of("token",token,"id",row.get("id"),"expiresAt",row.get("expiresAt"));
 }
 void customer(long id){if(Boolean.FALSE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM penny_customers WHERE id=? AND deleted_at IS NULL)",Boolean.class,id)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente non disponibile");}
 public List<Map<String,Object>> candidates(List<String> emails,String q){
  if(emails==null||emails.size()>201||q==null||q.length()>100)throw MailPayload.bad("Ricerca non valida");
  var normalized=emails.stream().filter(Objects::nonNull).map(String::trim).map(s->s.toLowerCase(Locale.ROOT)).filter(s->s.length()<=320&&!s.isBlank()).distinct().toList();
  var named=new NamedParameterJdbcTemplate(db);var p=new MapSqlParameterSource("emails",normalized.isEmpty()?List.of(""):normalized).addValue("q","%"+q.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");
  String match="(lower(trim(c.email)) IN (:emails) OR lower(trim(c.pec)) IN (:emails) OR EXISTS(SELECT 1 FROM penny_contact_customers l JOIN penny_customer_contacts p ON p.id=l.contact_id WHERE l.customer_id=c.id AND p.deleted_at IS NULL AND lower(trim(p.email)) IN (:emails)))";
  return named.queryForList("SELECT c.id,c.name,c.archived,"+match+" AS matched FROM penny_customers c WHERE c.deleted_at IS NULL AND ("+match+" OR (:q<>'%%' AND lower(c.name) LIKE :q)) ORDER BY matched DESC,c.name,c.id LIMIT 50",p);
 }
 @Transactional public Map<String,Object> save(MailPayload input,Authentication a){var user=require(a,true);var checked=input.checked();customer(input.customerId());
  var ids=db.queryForList("INSERT INTO penny_mail_messages(customer_id,message_key,internet_message_id,conversation_key,subject,sender,recipients,sent_at,body_text,original_name,original_data,original_kind,saved_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(customer_id,message_key) DO NOTHING RETURNING id",input.customerId(),checked.key(),input.internetMessageId(),checked.conversation(),Objects.toString(input.subject(),"(Senza oggetto)"),input.sender(),String.join(", ",input.recipients()),java.sql.Timestamp.from(input.sentAt()),Objects.toString(input.bodyText(),""),"messaggio."+(input.originalKind().equals("MSG")?"msg":"eml"),checked.original(),input.originalKind(),user.id);
  if(ids.isEmpty()){long id=db.queryForObject("SELECT id FROM penny_mail_messages WHERE customer_id=? AND message_key=?",Long.class,input.customerId(),checked.key());return Map.of("id",id,"duplicate",true);}
  long id=((Number)ids.getFirst().get("id")).longValue();for(int i=0;i<input.attachments().size();i++){var f=input.attachments().get(i);db.update("INSERT INTO penny_mail_attachments(message_id,filename,content,inline_image) VALUES (?,?,?,?)",id,f.filename(),checked.files().get(i),Boolean.TRUE.equals(f.inlineImage()));}
  audit.record(a,"MAIL_SAVED","MAIL",id,Map.of("customerId",input.customerId(),"attachments",input.attachments().size()));return Map.of("id",id,"duplicate",false);
 }
 Map<String,Object> message(long id){var rows=db.queryForList("SELECT m.id,m.conversation_key AS \"conversationKey\",m.customer_id AS \"customerId\",m.subject,m.sender,m.recipients,m.sent_at AS \"sentAt\",m.body_text AS \"bodyText\",m.original_kind AS \"originalKind\",m.saved_at AS \"savedAt\",u.full_name AS \"savedBy\" FROM penny_mail_messages m JOIN penny_customers c ON c.id=m.customer_id JOIN penny_users u ON u.id=m.saved_by WHERE m.id=? AND c.deleted_at IS NULL",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Email non disponibile");return rows.getFirst();}
 long record(long id,Authentication a,boolean write){var rows=db.queryForList("SELECT r.customer_id,r.kind FROM penny_customer_records r JOIN penny_customers c ON c.id=r.customer_id WHERE r.id=? AND r.deleted_at IS NULL AND c.deleted_at IS NULL",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attività non disponibile");var row=rows.getFirst();rbac.require(users.findByEmailIgnoreCase(a.getName()).orElse(null),it.penny.api.records.CustomerRecordService.permission((String)row.get("kind"))+(write?"_WRITE":"_READ"));return ((Number)row.get("customer_id")).longValue();}
}
