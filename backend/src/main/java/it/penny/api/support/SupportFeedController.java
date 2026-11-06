package it.penny.api.support;
import it.penny.api.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/support-feed")
public class SupportFeedController {
 final JdbcTemplate db;final PennyUserRepository users;
 public SupportFeedController(JdbcTemplate db,PennyUserRepository users){this.db=db;this.users=users;}
 PennyUser user(Authentication a){var u=a==null?null:users.findByEmailIgnoreCase(a.getName()).orElse(null);if(u==null||!u.enabled||u.mustChangePassword)throw new ResponseStatusException(HttpStatus.FORBIDDEN);return u;}
 // Shared operational board, deliberately excludes descriptions, comments, email,
 // customer IDs and closed-call history. Detailed records keep existing RBAC.
 @GetMapping public Map<String,Object> feed(@RequestParam(defaultValue="0") int page,@RequestParam(required=false) Long customerId,Authentication a){var u=user(a);if(page<0||page>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Pagina non valida");String from=" FROM penny_support_calls s JOIN penny_customer_records r ON r.id=s.record_id JOIN penny_customers c ON c.id=r.customer_id JOIN penny_users u ON u.id=s.technician_id WHERE s.outcome='IN_PROGRESS' AND r.deleted_at IS NULL AND c.deleted_at IS NULL AND (?::bigint IS NULL OR r.customer_id=?)";
  return Map.of("items",db.queryForList("SELECT s.record_id AS id,r.reference,c.name AS \"customerName\",s.requester_name AS \"requesterName\",u.full_name AS \"technicianName\",s.started_at AS \"startedAt\""+from+" ORDER BY s.started_at DESC,s.record_id DESC LIMIT 20 OFFSET ?",customerId,customerId,page*20),"total",db.queryForObject("SELECT count(*)"+from,Long.class,customerId,customerId),"notifications",db.queryForList("SELECT n.id,n.message,n.support_record_id AS \"supportId\",n.created_at AS \"createdAt\" FROM penny_notifications n JOIN penny_support_calls s ON s.record_id=n.support_record_id JOIN penny_customer_records r ON r.id=s.record_id JOIN penny_customers c ON c.id=r.customer_id WHERE n.user_id=? AND n.status='UNREAD' AND s.outcome='IN_PROGRESS' AND r.deleted_at IS NULL AND c.deleted_at IS NULL ORDER BY n.id DESC LIMIT 20",u.id),"unread",db.queryForObject("SELECT count(*) FROM penny_notifications n JOIN penny_support_calls s ON s.record_id=n.support_record_id JOIN penny_customer_records r ON r.id=s.record_id JOIN penny_customers c ON c.id=r.customer_id WHERE n.user_id=? AND n.status='UNREAD' AND s.outcome='IN_PROGRESS' AND r.deleted_at IS NULL AND c.deleted_at IS NULL",Long.class,u.id));
 }
 @PostMapping("/notifications/{id}/read") public Map<String,Object> read(@PathVariable long id,Authentication a){var u=user(a);if(db.update("UPDATE penny_notifications SET status='READ',updated_at=now() WHERE id=? AND user_id=? AND support_record_id IS NOT NULL AND status='UNREAD'",id,u.id)==0&&!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM penny_notifications WHERE id=? AND user_id=? AND support_record_id IS NOT NULL)",Boolean.class,id,u.id)))throw new ResponseStatusException(HttpStatus.NOT_FOUND);return Map.of("read",true);}
}
