package it.penny.api.products;

import it.penny.api.security.PennyUserRepository;
import it.penny.api.common.AuditLog;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import it.penny.api.security.RbacService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/catalog-products")
public class CatalogProductController {
 private final JdbcTemplate db;
 private final PennyUserRepository users;
 private final RbacService rbac;
 private final AuditLog audit;
 public CatalogProductController(JdbcTemplate db,PennyUserRepository users,RbacService rbac,AuditLog audit){this.db=db;this.users=users;this.rbac=rbac;this.audit=audit;}
 private void require(Authentication auth,String permission){rbac.require(auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null),permission);}
 static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 static String clean(String value,int max){if(value==null)return null;String s=value.trim();if(s.length()>max)throw bad("Campo troppo lungo: "+max);return s.isEmpty()?null:s;}
 static String required(String value,int max,String label){String s=clean(value,max);if(s==null)throw bad(label+" obbligatorio");return s;}
 static void category(String value){if(value==null||!Set.of("HARDWARE","SOFTWARE","SERVICE").contains(value))throw bad("Categoria non valida");}
 static void catalogCategory(String value){if(value==null||!Set.of("HARDWARE","SOFTWARE","SERVICE","TRAINING").contains(value))throw bad("Categoria non valida");}
 static BigDecimal price(JsonNode value){
  if(value==null||value.isNull())return null;
  if(!value.isNumber()&&!value.isTextual())throw bad("Prezzo non valido");
  try{BigDecimal amount=new BigDecimal(value.asText());if(amount.scale()>2||amount.signum()<0||amount.compareTo(new BigDecimal("999999999.99"))>0)throw bad("Prezzo non valido: massimo 999999999.99 EUR e due decimali");return amount;}
  catch(NumberFormatException e){throw bad("Prezzo non valido");}
 }
 private static String supplier(JsonNode value){if(value==null||value.isNull())return null;if(!value.isTextual())throw bad("Fornitore non valido");return clean(value.asText(),120);}
 static void origin(String value){if(value==null||!Set.of("INTERNAL","THIRD_PARTY").contains(value))throw bad("Origine non valida");}
 static String like(String q){String s=q.trim();if(s.length()>100)throw bad("Ricerca troppo lunga");return "%"+s.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
 static void pagination(int page,int size){if(page<0||page>100000||size<1||size>100)throw bad("Paginazione non valida");}
 private static final String FROM=" FROM penny_catalog_products p JOIN penny_product_categories c ON c.id=p.category_id LEFT JOIN penny_product_types t ON t.id=p.product_type_id";
 private static final String FIELDS="p.warranty_years AS \"warrantyYears\",p.margin_management AS \"marginManagement\",p.margin_profile_id AS \"marginProfileId\",p.id,p.code,p.name,p.category,p.origin,p.manufacturer,p.model,p.description,p.active,p.supplier,p.price,p.created_at AS \"createdAt\",p.updated_at AS \"updatedAt\",p.category_id AS \"categoryId\",c.name AS \"categoryName\",c.operational_kind AS \"operationalKind\",c.active AS \"categoryActive\",p.product_type_id AS \"productTypeId\",t.name AS \"productTypeName\",coalesce(t.obsolete,false) AS \"productTypeObsolete\",p.unit,p.supply_channel AS \"supplyChannel\",(p.image_data IS NOT NULL) AS \"hasImage\"";
 public record Input(String code,String name,String category,String origin,String manufacturer,String model,String description,Boolean active,JsonNode supplier,JsonNode price,JsonNode categoryId,JsonNode productTypeId,JsonNode unit,JsonNode supplyChannel,JsonNode warrantyYears,JsonNode marginManagement,JsonNode marginProfileId){
 public Input(String code,String name,String category,String origin,String manufacturer,String model,String description,Boolean active,JsonNode supplier,JsonNode price,JsonNode categoryId,JsonNode productTypeId,JsonNode unit,JsonNode supplyChannel){this(code,name,category,origin,manufacturer,model,description,active,supplier,price,categoryId,productTypeId,unit,supplyChannel,null,null,null);}
 }
 private Integer integer(JsonNode node){if(!node.isIntegralNumber()||!node.canConvertToInt()||node.intValue()<0||node.intValue()>100)throw bad("Anni di garanzia non validi");return node.intValue();}
 private Long id(JsonNode node){if(node==null||node.isNull())return null;if(!node.isIntegralNumber()||!node.canConvertToLong()||node.longValue()<1)throw bad("Identificativo non valido");return node.longValue();}
 private String field(JsonNode node,Object previous,int max){if(node==null)return (String)previous;if(node.isNull())return null;if(!node.isTextual())throw bad("Testo non valido");return clean(node.textValue(),max);}
 private Object[] values(Input in,Map<String,Object> old){
  if(in==null)throw bad("Dati obbligatori");origin(in.origin());Long cat=id(in.categoryId());
  if(in.categoryId()==null&&old!=null&&Objects.equals(in.category(),old.get("category")))cat=((Number)old.get("category_id")).longValue();
  if(cat==null){catalogCategory(in.category());cat=db.queryForObject("SELECT id FROM penny_product_categories WHERE code=?",Long.class,in.category());}
  var categories=db.queryForList("SELECT legacy_category,active FROM penny_product_categories WHERE id=? FOR SHARE",cat);if(categories.isEmpty())throw bad("Categoria non trovata");var category=categories.getFirst();String legacy=(String)category.get("legacy_category");if(in.category()!=null&&!legacy.equals(in.category()))throw bad("Categoria incompatibile con la famiglia selezionata");
  if(!Boolean.TRUE.equals(category.get("active"))&&(old==null||!Objects.equals(old.get("category_id"),cat)))throw bad("Categoria obsoleta");
  Long type=in.productTypeId()==null&&old!=null&&Objects.equals(old.get("category_id"),cat)?(Long)old.get("product_type_id"):id(in.productTypeId());
  if(type!=null){var types=db.queryForList("SELECT obsolete FROM penny_product_types WHERE id=? AND category_id=? FOR SHARE",type,cat);if(types.isEmpty())throw bad("Tipologia non appartenente alla categoria");if(Boolean.TRUE.equals(types.getFirst().get("obsolete"))&&(old==null||!Objects.equals(old.get("product_type_id"),type)))throw bad("Tipologia obsoleta");}
  String channel=field(in.supplyChannel(),old==null?null:old.get("supply_channel"),20);if(channel!=null&&!Set.of("INTERNAL_SALE","EXTERNAL_SUPPLY").contains(channel))throw bad("Canale non valido");
  Integer warranty=in.warrantyYears()==null?(old==null?null:(Integer)old.get("warranty_years")):in.warrantyYears().isNull()?null:integer(in.warrantyYears());
  String management=field(in.marginManagement(),old==null?"NONE":old.get("margin_management"),20);if(!Set.of("NONE","AUTOMATIC").contains(management==null?"NONE":management))throw bad("Gestione margini non valida");
  Long profile=in.marginProfileId()==null?(old==null?null:(Long)old.get("margin_profile_id")):id(in.marginProfileId());
  if(profile!=null&&db.queryForObject("SELECT count(*) FROM penny_margin_profiles WHERE id=?",Integer.class,profile)==0)throw bad("Profilo margini inesistente");
  if("AUTOMATIC".equals(management)&&profile==null)throw bad("Profilo margini obbligatorio");
  return new Object[]{required(in.code(),80,"Codice"),required(in.name(),200,"Nome"),legacy,in.origin(),clean(in.manufacturer(),120),clean(in.model(),120),clean(in.description(),10000),in.active()==null||in.active(),in.supplier()==null&&old!=null?old.get("supplier"):supplier(in.supplier()),in.price()==null&&old!=null?old.get("price"):price(in.price()),cat,type,field(in.unit(),old==null?null:old.get("unit"),40),channel,warranty,management==null?"NONE":management,profile};
 }
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String q,@RequestParam(required=false) String origin,@RequestParam(required=false) String category,@RequestParam(required=false) Long categoryId,@RequestParam(required=false) Long productTypeId,@RequestParam(required=false) Boolean active,@RequestParam(required=false) Boolean installable,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="asc") String dir,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
  require(auth,"CATALOG_READ");pagination(page,size);if(origin!=null)origin(origin);if(category!=null)catalogCategory(category);String like=like(q);
  var sorts=Map.of("name","p.name","code","p.code","category","c.name","supplier","p.supplier","createdAt","p.created_at");if(!sorts.containsKey(sort)||!Set.of("asc","desc").contains(dir))throw bad("Ordinamento non valido");
  String where=" WHERE p.deleted_at IS NULL AND (CAST(? AS varchar) IS NULL OR p.origin=?) AND (CAST(? AS varchar) IS NULL OR p.category=?) AND (CAST(? AS boolean) IS NULL OR p.active=?) AND (CAST(? AS bigint) IS NULL OR p.category_id=?) AND (CAST(? AS bigint) IS NULL OR p.product_type_id=?) AND (lower(p.code) LIKE ? ESCAPE '\\' OR lower(p.name) LIKE ? ESCAPE '\\' OR lower(coalesce(p.manufacturer,'')) LIKE ? ESCAPE '\\' OR lower(coalesce(p.supplier,'')) LIKE ? ESCAPE '\\')";
  if(Boolean.TRUE.equals(installable))where+=" AND c.operational_kind IN ('HARDWARE','SOFTWARE','OTHER')";
  Object[] args={origin,origin,category,category,active,active,categoryId,categoryId,productTypeId,productTypeId,like,like,like,like};
  Long total=db.queryForObject("SELECT count(*)"+FROM+where,Long.class,args);Object[] pages=Arrays.copyOf(args,args.length+2);pages[args.length]=size;pages[args.length+1]=page*size;
  var items=db.queryForList("SELECT "+FIELDS+FROM+where+" ORDER BY "+sorts.get(sort)+" "+dir+" NULLS LAST,p.id LIMIT ? OFFSET ?",pages);return Map.of("items",items,"total",total==null?0:total,"page",page,"size",size);
 }
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable long id,Authentication auth){require(auth,"CATALOG_READ");var rows=db.queryForList("SELECT "+FIELDS+FROM+" WHERE p.id=? AND p.deleted_at IS NULL",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Prodotto di catalogo non trovato");return rows.getFirst();}
 @PostMapping @Transactional public Map<String,Object> create(@RequestBody Input in,Authentication auth){require(auth,"CATALOG_WRITE");var p=values(in,null);long id=db.queryForObject("INSERT INTO penny_catalog_products(code,name,category,origin,manufacturer,model,description,active,supplier,price,category_id,product_type_id,unit,supply_channel,warranty_years,margin_management,margin_profile_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,p);audit.record(auth,"CREATE","CATALOG_PRODUCT",id,Map.of());return Map.of("id",id);}
 @PutMapping("/{id}") @Transactional public Map<String,Object> update(@PathVariable long id,@RequestBody Input in,Authentication auth){require(auth,"CATALOG_WRITE");var rows=db.queryForList("SELECT * FROM penny_catalog_products WHERE id=? AND deleted_at IS NULL FOR UPDATE",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Prodotto di catalogo non trovato");var p=values(in,rows.getFirst());var args=Arrays.copyOf(p,p.length+1);args[p.length]=id;db.update("UPDATE penny_catalog_products SET code=?,name=?,category=?,origin=?,manufacturer=?,model=?,description=?,active=?,supplier=?,price=?,category_id=?,product_type_id=?,unit=?,supply_channel=?,warranty_years=?,margin_management=?,margin_profile_id=?,updated_at=now() WHERE id=?",args);audit.record(auth,"UPDATE","CATALOG_PRODUCT",id,Map.of());return Map.of("updated",true);}
 @PostMapping("/{id}/image") @Transactional public Map<String,Object> image(@PathVariable long id,@RequestParam("file") org.springframework.web.multipart.MultipartFile file,Authentication auth)throws java.io.IOException{
  require(auth,"CATALOG_WRITE");if(file.isEmpty()||file.getSize()>5242880)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Immagine troppo grande: massimo 5 MB");byte[] bytes=file.getBytes();String mime=null;
  if(bytes.length>=8&&java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))mime="image/png";
  else if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)mime="image/jpeg";
  else if(bytes.length>=12&&new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))mime="image/webp";
  if(mime==null)throw bad("Carica un'immagine PNG, JPEG o WEBP");
  if(db.update("UPDATE penny_catalog_products SET image_data=?,image_mime=?,updated_at=now() WHERE id=? AND deleted_at IS NULL",bytes,mime,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Prodotto non trovato");audit.record(auth,"CATALOG_IMAGE_UPDATED","CATALOG_PRODUCT",id,Map.of("bytes",bytes.length));return Map.of("updated",true);
 }
 @GetMapping("/{id}/image") public org.springframework.http.ResponseEntity<byte[]> image(@PathVariable long id,Authentication auth){require(auth,"CATALOG_READ");var rows=db.queryForList("SELECT image_data,image_mime FROM penny_catalog_products WHERE id=? AND deleted_at IS NULL",id);if(rows.isEmpty()||rows.getFirst().get("image_data")==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Immagine non presente");return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").contentType(org.springframework.http.MediaType.parseMediaType((String)rows.getFirst().get("image_mime"))).body((byte[])rows.getFirst().get("image_data"));}
 @DeleteMapping("/{id}/image") @Transactional public Map<String,Object> deleteImage(@PathVariable long id,Authentication auth){require(auth,"CATALOG_WRITE");if(db.update("UPDATE penny_catalog_products SET image_data=null,image_mime=null,updated_at=now() WHERE id=? AND deleted_at IS NULL",id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Prodotto non trovato");audit.record(auth,"CATALOG_IMAGE_REMOVED","CATALOG_PRODUCT",id,Map.of());return Map.of("deleted",true);}
}
