package it.penny.api.margins;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.penny.api.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.*;
import java.util.*;
@Service
public class MarginService {
 private final JdbcTemplate db;private final ObjectMapper json;private final PennyUserRepository users;private final RbacService rbac;
 public MarginService(JdbcTemplate db,ObjectMapper json,PennyUserRepository users,RbacService rbac){this.db=db;this.json=json;this.users=users;this.rbac=rbac;}
 // Invoke accessors through the Spring proxy: its inherited instance fields are uninitialized.
 public JdbcTemplate database(){return db;}
 public ObjectMapper mapper(){return json;}
 void require(Authentication auth,String permission){rbac.require(auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null),permission);}
 static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 static String reason(String value){if(value==null||value.isBlank()||value.length()>2000)throw bad("Motivazione obbligatoria (massimo 2000 caratteri)");return value.trim();}
 Map<String,Object> object(Object value){try{return json.readValue(value==null?"{}":value.toString(),new TypeReference<>(){});}catch(Exception e){throw bad("Dati margini non validi");}}
 String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw bad("Dati non validi");}}
 Map<String,Object> row(String table,long id){var rows=db.queryForList("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Elemento non trovato");return rows.getFirst();}
 @Transactional public void snapshot(long saleId,Authentication auth){snapshot(saleId,auth,false,"Calcolo iniziale");}
 @Transactional public void reevaluate(long saleId,String reason,Authentication auth){require(auth,"MARGINS_WRITE");snapshot(saleId,auth,true,reason(reason));}
 private void snapshot(long saleId,Authentication auth,boolean again,String reason){
  var sale=row("penny_sales",saleId);if("CANCELLED".equals(sale.get("status")))throw bad("Vendita annullata");
  int revision=db.queryForObject("SELECT coalesce(max(revision),0) FROM penny_margin_snapshots WHERE sale_id=?",Integer.class,saleId);if(revision>0&&!again)return;
  var products=db.queryForList("SELECT margin_profile_id,margin_management,product_type_id,category_id FROM penny_catalog_products WHERE id=?",sale.get("catalog_product_id"));if(products.isEmpty())return;var product=products.getFirst();
  if(again){for(var old:db.queryForList("SELECT id FROM penny_margin_snapshots WHERE sale_id=? AND revision=?",saleId,revision))event(((Number)old.get("id")).longValue(),"SUPERSEDED",BigDecimal.ZERO,reason,auth);}
  if(!"AUTOMATIC".equals(product.get("margin_management"))||product.get("margin_profile_id")==null)return;
  Map<String,Object> context=new HashMap<>(object(sale.get("business")));context.putIfAbsent("productTypeId",product.get("product_type_id"));context.putIfAbsent("categoryId",product.get("category_id"));
  var profile=row("penny_margin_profiles",((Number)product.get("margin_profile_id")).longValue());List<String> components;try{components=json.readValue(profile.get("components").toString(),new TypeReference<>(){});}catch(Exception e){throw bad("Profilo non valido");}
  BigDecimal base=((BigDecimal)sale.get("unit_price")).multiply(new BigDecimal(sale.get("quantity").toString()));
  for(String component:components){var rules=db.queryForList("SELECT * FROM penny_margin_rules WHERE profile_id=? AND component=? AND status='ACTIVE' AND valid_from<=? AND (valid_to IS NULL OR valid_to>=?) ORDER BY priority DESC,id DESC",product.get("margin_profile_id"),component,sale.get("sold_on"),sale.get("sold_on"));var selected=rules.stream().filter(r->MarginCalculator.matches(object(r.get("conditions")),context)).findFirst();Map<String,Object> rule=selected.orElse(Map.of());BigDecimal amount=selected.isEmpty()?BigDecimal.ZERO:MarginCalculator.calculate(base,(String)rule.get("calculation"),(BigDecimal)rule.get("value"),((Number)rule.get("sign")).intValue());
   db.update("INSERT INTO penny_margin_snapshots(sale_id,revision,component,rule_id,rule_snapshot,applicable,calculated,created_by) VALUES (?,?,?,?,?::jsonb,?,?,?)",saleId,revision+1,component,rule.get("id"),encode(Map.of("rule",rule,"context",context,"base",base,"reason",reason)),selected.isPresent(),amount,auth.getName());
  }
 }
 void event(long id,String kind,BigDecimal amount,String reason,Authentication auth){db.update("INSERT INTO penny_margin_events(snapshot_id,kind,amount,reason,actor) VALUES (?,?,?,?,?)",id,kind,amount,reason,auth.getName());}
 @Transactional public void change(long id,String kind,BigDecimal amount,String reason,Authentication auth){require(auth,"MARGINS_WRITE");var s=row("penny_margin_snapshots",id);var sale=row("penny_sales",((Number)s.get("sale_id")).longValue());if(!"CONFIRMED".equals(sale.get("status")))throw bad("Vendita annullata");
  if(db.queryForObject("SELECT count(*) FROM penny_margin_events WHERE snapshot_id=? AND kind IN ('SUPERSEDED','CANCELLED')",Integer.class,id)>0)throw bad("Margine annullato o sostituito");
  int confirmed=db.queryForObject("SELECT count(*) FROM penny_margin_events WHERE snapshot_id=? AND kind='CONFIRMED'",Integer.class,id);
  if("CONFIRMED".equals(kind)){if(!Boolean.TRUE.equals(s.get("applicable"))||confirmed>0)throw bad("Margine non applicabile o già confermato");amount=(BigDecimal)s.get("calculated");reason="Conferma margine";}
  else{reason=reason(reason);if("ADJUSTED".equals(kind)){if(confirmed==0||amount==null||amount.signum()==0||amount.stripTrailingZeros().scale()>2||amount.abs().compareTo(new BigDecimal("999999999999999999.99"))>0)throw bad("Rettifica non valida");}else if("CANCELLED".equals(kind))amount=BigDecimal.ZERO;else throw bad("Operazione non valida");}
  event(id,kind,amount,reason,auth);
 }
 @Transactional public void cancelSale(long saleId,String reason,Authentication auth){row("penny_sales",saleId);for(var s:db.queryForList("SELECT id FROM penny_margin_snapshots WHERE sale_id=?",saleId))event(((Number)s.get("id")).longValue(),"CANCELLED",BigDecimal.ZERO,reason(reason),auth);}
 List<Map<String,Object>> report(Long saleId){return db.queryForList("SELECT s.id,s.sale_id AS \"saleId\",s.revision,s.component,s.rule_id AS \"ruleId\",s.applicable,s.calculated::text AS calculated,s.rule_snapshot AS \"ruleSnapshot\",CASE WHEN bool_or(e.kind IN ('CANCELLED','SUPERSEDED')) THEN 0 ELSE coalesce(sum(e.amount),0) END::numeric(20,2)::text AS recognized,CASE WHEN bool_or(e.kind='SUPERSEDED') THEN 'SUPERSEDED' WHEN bool_or(e.kind='CANCELLED') THEN 'CANCELLED' WHEN bool_or(e.kind='ADJUSTED') THEN 'ADJUSTED' WHEN bool_or(e.kind='CONFIRMED') THEN 'CONFIRMED' ELSE 'TO_VERIFY' END AS status,s.revision=(SELECT max(x.revision) FROM penny_margin_snapshots x WHERE x.sale_id=s.sale_id) AND NOT coalesce(bool_or(e.kind='SUPERSEDED'),false) AS current FROM penny_margin_snapshots s LEFT JOIN penny_margin_events e ON e.snapshot_id=s.id WHERE (?::bigint IS NULL OR s.sale_id=?) GROUP BY s.id ORDER BY s.sale_id DESC,s.revision DESC,s.id",saleId,saleId);}
}
