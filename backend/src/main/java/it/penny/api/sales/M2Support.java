package it.penny.api.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.penny.api.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
class M2Support {
 final JdbcTemplate db; final it.penny.api.common.AuditLog audit; final PennyUserRepository users; final RbacService rbac;
 M2Support(JdbcTemplate db,it.penny.api.common.AuditLog audit,PennyUserRepository users,RbacService rbac){this.db=db;this.audit=audit;this.users=users;this.rbac=rbac;}
 PennyUser user(Authentication auth){return auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null);}
 void require(Authentication auth,String... permissions){var u=user(auth);for(String permission:permissions)rbac.require(u,permission);}
 boolean allowed(PennyUser user,String permission){return user!=null&&user.enabled&&!user.mustChangePassword&&rbac.allowed(user.id,permission);}
 static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
 static ResponseStatusException missing(String label){return new ResponseStatusException(HttpStatus.NOT_FOUND,label+" non trovato");}
 static String clean(String value,int max){if(value==null)return null;String s=value.trim();if(s.length()>max)throw bad("Campo troppo lungo: "+max+" caratteri");return s.isEmpty()?null:s;}
 static String required(String value,int max,String label){String s=clean(value,max);if(s==null)throw bad(label+" obbligatorio");return s;}
 static int quantity(Integer value){if(value==null||value<1||value>1000000)throw bad("Quantità intera da 1 a 1000000");return value;}
 static void pagination(int page,int size){if(page<0||page>100000||size<1||size>100)throw bad("Paginazione non valida");}
 static String like(String q){String s=q.trim();if(s.length()>100)throw bad("Ricerca troppo lunga");return "%"+s.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
 void customer(Long id,boolean active){if(id==null||id<1||db.queryForObject("SELECT count(*) FROM penny_customers WHERE id=? AND deleted_at IS NULL"+(active?" AND NOT archived":""),Integer.class,id)==0)throw bad("Cliente inesistente o archiviato");}
 Map<String,Object> row(String table,long id,boolean lock){var rows=db.queryForList("SELECT * FROM "+table+" WHERE id=?"+(Set.of("penny_workstations","penny_installed_products").contains(table)?" AND deleted_at IS NULL":"")+(lock?" FOR UPDATE":""),id);if(rows.isEmpty())throw missing("Elemento");return rows.getFirst();}
 void audit(Authentication auth,String action,String type,long id,Map<String,?> data){audit.record(auth,action,type,id,data);}
 static Map<String,Object> money(Map<String,Object> row){row.replaceAll((k,v)->v instanceof BigDecimal d&&Set.of("unitPrice","totalAmount","salesAmount").contains(k)?d.setScale(2).toPlainString():v);return row;}
 static List<Map<String,Object>> money(List<Map<String,Object>> rows){rows.forEach(M2Support::money);return rows;}
 static void dates(LocalDate from,LocalDate to){if(from!=null&&to!=null&&to.isBefore(from))throw bad("La data finale precede quella iniziale");}
}
