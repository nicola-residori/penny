package it.penny.api.images;

import it.penny.api.security.*;
import it.penny.api.common.AuditLog;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/entity-images")
public class EntityImageController {
 private record Resource(String table,String read,String write,String live) {}
 private static final Map<String,Resource> RESOURCES=Map.of(
  "customers",new Resource("penny_customers","CUSTOMERS_READ","CUSTOMERS_WRITE","deleted_at IS NULL"),
  "contacts",new Resource("penny_customer_contacts","CUSTOMERS_READ","CUSTOMERS_WRITE","deleted_at IS NULL AND EXISTS(SELECT 1 FROM penny_contact_customers l JOIN penny_customers c ON c.id=l.customer_id WHERE l.contact_id=penny_customer_contacts.id AND c.deleted_at IS NULL)"),
  "installed-products",new Resource("penny_installed_products","PRODUCTS_READ","PRODUCTS_WRITE","deleted_at IS NULL AND EXISTS(SELECT 1 FROM penny_customers c WHERE c.id=penny_installed_products.customer_id AND c.deleted_at IS NULL)"));
 private final JdbcTemplate db; private final PennyUserRepository users; private final RbacService rbac; private final AuditLog audit;
 public EntityImageController(JdbcTemplate db,PennyUserRepository users,RbacService rbac,AuditLog audit){this.db=db;this.users=users;this.rbac=rbac;this.audit=audit;}
 private Resource resource(String kind,Authentication auth,boolean write){
  Resource r=RESOURCES.get(kind);if(r==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  rbac.require(auth==null?null:users.findByEmailIgnoreCase(auth.getName()).orElse(null),write?r.write:r.read);return r;
 }
 static String mime(byte[] bytes){
  if(bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
  if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)return "image/jpeg";
  if(bytes.length>=12&&new String(bytes,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Carica un'immagine PNG, JPEG o WEBP");
 }
 private Map<String,Object> row(Resource r,long id){
  var rows=db.queryForList("SELECT image_data,image_mime"+(r.table.equals("penny_installed_products")?",catalog_product_id":"")+" FROM "+r.table+" WHERE id=? AND "+r.live,id);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Scheda non disponibile");return rows.getFirst();
 }
 private Map<String,Object> effective(Resource r,Map<String,Object> own){
  if(own.get("image_data")==null&&r.table.equals("penny_installed_products")&&own.get("catalog_product_id")!=null){
   var catalog=db.queryForList("SELECT image_data,image_mime FROM penny_catalog_products WHERE id=? AND deleted_at IS NULL",own.get("catalog_product_id"));
   if(!catalog.isEmpty())return catalog.getFirst();
  }
  return own;
 }
 private Map<String,Object> metadata(Resource r,long id){var own=row(r,id);return Map.of("hasOwnImage",own.get("image_data")!=null,"hasImage",effective(r,own).get("image_data")!=null);}
 @GetMapping("/{kind}/{id}") public ResponseEntity<byte[]> get(@PathVariable String kind,@PathVariable long id,Authentication auth){
  var r=resource(kind,auth,false);var image=effective(r,row(r,id));
  if(image.get("image_data")==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Immagine non presente");
  return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType((String)image.get("image_mime"))).body((byte[])image.get("image_data"));
 }
 @PostMapping("/{kind}/{id}") @Transactional public Map<String,Object> upload(@PathVariable String kind,@PathVariable long id,@RequestParam("file") MultipartFile file,Authentication auth)throws IOException{
  var r=resource(kind,auth,true);
  if(file.isEmpty()||file.getSize()>5242880)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Scegli un'immagine fino a 5 MB");
  byte[] bytes=file.getBytes();String type=mime(bytes);
  if(db.update("UPDATE "+r.table+" SET image_data=?,image_mime=?"+(r.table.equals("penny_customer_contacts")?"":",updated_at=now()")+" WHERE id=? AND "+r.live,bytes,type,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Scheda non disponibile");
  audit.record(auth,"IMAGE_UPDATED",kind.toUpperCase(Locale.ROOT),id,Map.of("bytes",bytes.length));return metadata(r,id);
 }
 @DeleteMapping("/{kind}/{id}") @Transactional public Map<String,Object> remove(@PathVariable String kind,@PathVariable long id,Authentication auth){
  var r=resource(kind,auth,true);
  if(db.update("UPDATE "+r.table+" SET image_data=null,image_mime=null"+(r.table.equals("penny_customer_contacts")?"":",updated_at=now()")+" WHERE id=? AND "+r.live,id)==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Scheda non disponibile");
  audit.record(auth,"IMAGE_REMOVED",kind.toUpperCase(Locale.ROOT),id,Map.of());return metadata(r,id);
 }
}
