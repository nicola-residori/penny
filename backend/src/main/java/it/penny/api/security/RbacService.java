package it.penny.api.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RbacService {
 private final JdbcTemplate db;
 public RbacService(JdbcTemplate db){this.db=db;}
 public boolean allowed(Long userId,String permission){
  var direct=db.query("SELECT effect FROM penny_user_permissions WHERE user_id=? AND permission_code=?",(rs,n)->rs.getString(1),userId,permission);
  if(!direct.isEmpty())return "ALLOW".equals(direct.get(0));
  var effects=db.query("SELECT rp.effect FROM penny_role_permissions rp JOIN penny_user_roles ur ON ur.role_id=rp.role_id WHERE ur.user_id=? AND rp.permission_code=?",(rs,n)->rs.getString(1),userId,permission);
  return !effects.contains("DENY") && effects.contains("ALLOW");
 }
 public void require(PennyUser user,String permission){if(user==null || !user.enabled || user.mustChangePassword || !allowed(user.id,permission))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Permesso insufficiente");}
}
