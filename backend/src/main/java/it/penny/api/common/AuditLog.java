package it.penny.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.Map;

/** Call inside the domain mutation transaction; record identifiers, never secrets. */
@Component
public class AuditLog {
 private final JdbcTemplate db;private final ObjectMapper json;
 public AuditLog(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 public void record(Authentication auth,String action,String type,long id,Map<String,?> data){
  try{db.update("INSERT INTO penny_m2_audit(actor,action,entity_type,entity_id,metadata) VALUES (?,?,?,?,CAST(? AS jsonb))",auth.getName(),action,type,id,json.writeValueAsString(data));}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Audit serialization failed",e);}
 }
}
