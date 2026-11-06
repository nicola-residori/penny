package it.penny.api.mail;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/outlook/bridge")
public class OutlookBridgeController {
 final MailService s;final org.springframework.jdbc.core.JdbcTemplate db;
 public OutlookBridgeController(MailService s,org.springframework.jdbc.core.JdbcTemplate db){this.s=s;this.db=db;}
 public record Search(List<String> emails,String q){}
 @PostMapping("/customers") public List<Map<String,Object>> candidates(@RequestHeader(value="Authorization",required=false) String token,@RequestBody Search input){s.bearer(token);return s.candidates(input.emails(),Objects.toString(input.q(),""));}
 @PostMapping("/messages") public Map<String,Object> save(@RequestHeader(value="Authorization",required=false) String token,@RequestBody MailPayload input){return s.save(input,s.bearer(token));}
 @DeleteMapping("/connection") public Map<String,Object> disconnect(@RequestHeader(value="Authorization",required=false) String token){s.bearer(token);db.update("UPDATE penny_mail_connections SET revoked_at=now() WHERE token_hash=?",MailPayload.hash(token.substring(7)));return Map.of("revoked",true);}
}
