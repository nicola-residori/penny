package it.penny.api.admin;

import it.penny.api.security.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.*;

@RestController @RequestMapping("/api/admin")
public class AdminController {
 private final JdbcTemplate db; private final PennyUserRepository users; private final PasswordEncoder encoder; private final RbacService rbac;
 private final SecureRandom random=new SecureRandom();
 public AdminController(JdbcTemplate db,PennyUserRepository users,PasswordEncoder encoder,RbacService rbac){this.db=db;this.users=users;this.encoder=encoder;this.rbac=rbac;}
 private PennyUser actor(Authentication auth,String permission){PennyUser u=users.findByEmailIgnoreCase(auth.getName()).orElse(null);rbac.require(u,permission);return u;}
 private PennyUser target(long id){return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Utente non trovato"));}
 private void bad(String msg){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
 private String temporaryPassword(){String chars="ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";StringBuilder s=new StringBuilder();for(int i=0;i<20;i++)s.append(chars.charAt(random.nextInt(chars.length())));return s.toString();}
 private void ensureOtherAdmin(long userId){
  Long remaining=db.queryForObject("SELECT count(*) FROM penny_users u JOIN penny_user_roles ur ON ur.user_id=u.id JOIN penny_roles r ON r.id=ur.role_id WHERE r.code='ADMIN' AND u.enabled AND u.id<>?",Long.class,userId);
  if(remaining==null || remaining==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Non puoi rimuovere l'ultimo amministratore attivo");
 }
 public record CreateUser(String email,String fullName,Boolean enabled){}
 public record UpdateUser(String fullName,Boolean enabled){}
 public record RoleInput(String code,String description){}
 public record EffectInput(String effect){}
 @GetMapping("/users") public List<Map<String,Object>> users(Authentication auth){actor(auth,"USERS_READ");return db.queryForList("SELECT id,email,full_name AS \"fullName\",enabled,must_change_password AS \"mustChangePassword\" FROM penny_users ORDER BY id");}
 @PostMapping("/users") @Transactional public Map<String,Object> create(@RequestBody CreateUser input,Authentication auth){actor(auth,"USERS_WRITE");if(input.email()==null||!input.email().matches("(?i)^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")||input.fullName()==null||input.fullName().isBlank()||input.fullName().length()>200)bad("Dati utente non validi");String email=input.email().trim().toLowerCase(Locale.ROOT);if(users.findByEmailIgnoreCase(email).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Email già presente");String pw=temporaryPassword();PennyUser u=new PennyUser();u.email=email;u.fullName=input.fullName().trim();u.enabled=input.enabled()==null||input.enabled();u.passwordHash=encoder.encode(pw);u.mustChangePassword=true;users.saveAndFlush(u);return Map.of("id",u.id,"temporaryPassword",pw);}
 @PatchMapping("/users/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,@RequestBody UpdateUser input,Authentication auth){actor(auth,"USERS_WRITE");PennyUser u=target(id);if(input.fullName()!=null){if(input.fullName().isBlank()||input.fullName().length()>200)bad("Nome non valido");u.fullName=input.fullName().trim();}if(input.enabled()!=null&&u.enabled!=input.enabled()){if(!input.enabled())ensureOtherAdminIfAdmin(u.id);u.enabled=input.enabled();u.sessionVersion++;}users.save(u);return Map.of("updated",true);}
 private void ensureOtherAdminIfAdmin(long id){Long n=db.queryForObject("SELECT count(*) FROM penny_user_roles ur JOIN penny_roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='ADMIN'",Long.class,id);if(n!=null&&n>0)ensureOtherAdmin(id);}
 @PostMapping("/users/{id}/reset-password") @Transactional public Map<String,Object> reset(@PathVariable long id,Authentication auth){actor(auth,"USERS_WRITE");PennyUser u=target(id);String pw=temporaryPassword();u.passwordHash=encoder.encode(pw);u.mustChangePassword=true;u.sessionVersion++;users.save(u);return Map.of("temporaryPassword",pw);}
 @PostMapping("/users/{id}/force-password-change") @Transactional public Map<String,Object> force(@PathVariable long id,Authentication auth){actor(auth,"USERS_WRITE");PennyUser u=target(id);u.mustChangePassword=true;u.sessionVersion++;users.save(u);return Map.of("updated",true);}
 @GetMapping("/roles") public List<Map<String,Object>> roles(Authentication auth){actor(auth,"ROLES_READ");return db.queryForList("SELECT id,code,description FROM penny_roles ORDER BY code");}
 @PostMapping("/roles") @Transactional public Map<String,Object> createRole(@RequestBody RoleInput input,Authentication auth){actor(auth,"ROLES_WRITE");if(input.code()==null||!input.code().matches("[A-Z][A-Z0-9_]{1,79}")||input.description()==null||input.description().isBlank()||input.description().length()>200)bad("Ruolo non valido");db.update("INSERT INTO penny_roles(code,description) VALUES (?,?)",input.code(),input.description());return Map.of("created",true);}
 @GetMapping("/permissions") public List<Map<String,Object>> permissions(Authentication auth){actor(auth,"ROLES_READ");return db.queryForList("SELECT code,description FROM penny_permissions ORDER BY code");}
 @GetMapping("/users/{id}/roles") public List<Map<String,Object>> userRoles(@PathVariable long id,Authentication auth){actor(auth,"USERS_READ");target(id);return db.queryForList("SELECT r.id,r.code FROM penny_roles r JOIN penny_user_roles ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.code",id);}
 @PutMapping("/users/{id}/roles/{roleId}") @Transactional public Map<String,Object> assign(@PathVariable long id,@PathVariable long roleId,Authentication auth){actor(auth,"USERS_WRITE");target(id);db.update("INSERT INTO penny_user_roles(user_id,role_id) VALUES (?,?) ON CONFLICT DO NOTHING",id,roleId);return Map.of("updated",true);}
 @DeleteMapping("/users/{id}/roles/{roleId}") @Transactional public Map<String,Object> unassign(@PathVariable long id,@PathVariable long roleId,Authentication auth){actor(auth,"USERS_WRITE");target(id);Long n=db.queryForObject("SELECT count(*) FROM penny_roles WHERE id=? AND code='ADMIN'",Long.class,roleId);if(n!=null&&n>0)ensureOtherAdmin(id);db.update("DELETE FROM penny_user_roles WHERE user_id=? AND role_id=?",id,roleId);return Map.of("updated",true);}
 private boolean isAdminRole(long id){Long n=db.queryForObject("SELECT count(*) FROM penny_roles WHERE id=? AND code='ADMIN'",Long.class,id);return n!=null&&n>0;}
 private boolean isAdminUser(long id){Long n=db.queryForObject("SELECT count(*) FROM penny_user_roles ur JOIN penny_roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='ADMIN'",Long.class,id);return n!=null&&n>0;}
 private String effect(String e){if(!"ALLOW".equals(e)&&!"DENY".equals(e))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Effetto non valido");return e;}
 @GetMapping("/roles/{id}/permissions") public List<Map<String,Object>> rolePermissions(@PathVariable long id,Authentication auth){actor(auth,"ROLES_READ");return db.queryForList("SELECT permission_code AS \"permissionCode\",effect FROM penny_role_permissions WHERE role_id=? ORDER BY permission_code",id);}
 @PutMapping("/roles/{id}/permissions/{code}") @Transactional public Map<String,Object> rolePermission(@PathVariable long id,@PathVariable String code,@RequestBody EffectInput input,Authentication auth){actor(auth,"ROLES_WRITE");if(isAdminRole(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"I permessi del ruolo ADMIN sono protetti");db.update("INSERT INTO penny_role_permissions(role_id,permission_code,effect) VALUES (?,?,?) ON CONFLICT(role_id,permission_code) DO UPDATE SET effect=EXCLUDED.effect",id,code,effect(input.effect()));return Map.of("updated",true);}
 @DeleteMapping("/roles/{id}/permissions/{code}") public Map<String,Object> removeRolePermission(@PathVariable long id,@PathVariable String code,Authentication auth){actor(auth,"ROLES_WRITE");if(isAdminRole(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"I permessi del ruolo ADMIN sono protetti");db.update("DELETE FROM penny_role_permissions WHERE role_id=? AND permission_code=?",id,code);return Map.of("updated",true);}
 @GetMapping("/users/{id}/permissions") public List<Map<String,Object>> userPermissions(@PathVariable long id,Authentication auth){actor(auth,"USERS_READ");target(id);return db.queryForList("SELECT permission_code AS \"permissionCode\",effect FROM penny_user_permissions WHERE user_id=? ORDER BY permission_code",id);}
 @PutMapping("/users/{id}/permissions/{code}") @Transactional public Map<String,Object> userPermission(@PathVariable long id,@PathVariable String code,@RequestBody EffectInput input,Authentication auth){actor(auth,"USERS_WRITE");target(id);if(isAdminUser(id) && (code.equals("USERS_WRITE")||code.equals("ROLES_WRITE")) && "DENY".equals(input.effect()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Non puoi negare i permessi amministrativi a un amministratore");db.update("INSERT INTO penny_user_permissions(user_id,permission_code,effect) VALUES (?,?,?) ON CONFLICT(user_id,permission_code) DO UPDATE SET effect=EXCLUDED.effect",id,code,effect(input.effect()));return Map.of("updated",true);}
 @DeleteMapping("/users/{id}/permissions/{code}") public Map<String,Object> removeUserPermission(@PathVariable long id,@PathVariable String code,Authentication auth){actor(auth,"USERS_WRITE");target(id);db.update("DELETE FROM penny_user_permissions WHERE user_id=? AND permission_code=?",id,code);return Map.of("updated",true);}
}
