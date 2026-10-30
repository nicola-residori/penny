package it.penny.api.admin;

import it.penny.api.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@RestController
@RequestMapping("/api/admin/data")
public class DemoDataController {
 private final JdbcTemplate db;
 private final PennyUserRepository users;
 private final RbacService rbac;
 private final ObjectMapper json;
 private static final Map<String,String> TABLES;
 static {var tables=new LinkedHashMap<String,String>();
  tables.put("customers","penny_customers");tables.put("contacts","penny_customer_contacts");tables.put("contactLinks","penny_contact_customers");tables.put("sites","penny_customer_sites");tables.put("catalog","penny_catalog_products");tables.put("installations","penny_installed_products");tables.put("sales","penny_sales");tables.put("allocations","penny_sale_allocations");tables.put("workstations","penny_workstations");tables.put("trash","penny_trash_entries");tables.put("customerRecords","penny_customer_records");tables.put("recordParticipants","penny_record_participants");tables.put("orders","penny_sales_orders");tables.put("tasks","penny_record_tasks");tables.put("interventionLinks","penny_record_interventions");tables.put("renewals","penny_contract_renewals");tables.put("documents","penny_documents");tables.put("notifications","penny_notifications");tables.put("marginProfiles","penny_margin_profiles");tables.put("marginRules","penny_margin_rules");tables.put("marginSnapshots","penny_margin_snapshots");tables.put("marginEvents","penny_margin_events");tables.put("marginRuleAudit","penny_margin_rule_audit");TABLES=Collections.unmodifiableMap(tables);
 }
 public DemoDataController(JdbcTemplate db,PennyUserRepository users,RbacService rbac,ObjectMapper json){this.db=db;this.users=users;this.rbac=rbac;this.json=json;}
 private void require(Authentication auth){rbac.require(users.findByEmailIgnoreCase(auth.getName()).orElse(null),"DATA_MANAGE");}
 private Map<String,Long> counts(){var result=new LinkedHashMap<String,Long>();TABLES.forEach((key,table)->result.put(key,db.queryForObject("SELECT count(*) FROM "+table,Long.class)));return result;}
 private int scenarioVersion(){return db.queryForObject("SELECT coalesce(max(scenario_version),0) FROM penny_demo_environment",Integer.class);}
 private Map<String,Object> state(){int version=scenarioVersion();return Map.of("canManageData",true,"demoLoaded",version>0,"scenarioVersion",version,"demoUpgradeAvailable",version==1,"counts",counts());}
 @GetMapping("/state") public Map<String,Object> preview(Authentication auth){require(auth);return state();}
 public record DemoInput(Boolean confirm){}
 public record ResetInput(String confirmation){}
 private void lock(){db.queryForObject("SELECT pg_advisory_xact_lock(1122026)",(rs,row)->0);}
 private void audit(String actor,String action,Map<String,Long> counts){
  try{db.update("INSERT INTO penny_m2_audit(actor,action,entity_type,entity_id,metadata) VALUES (?,?,'ENVIRONMENT',0,?::jsonb)",actor,action,json.writeValueAsString(counts));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
 @PostMapping("/reset") @Transactional public Map<String,Object> reset(@RequestBody ResetInput input,Authentication auth){
  require(auth);if(!"RESET".equals(input.confirmation()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Scrivi RESET per confermare l'eliminazione di tutti i dati CRM");
  lock();var before=counts();
  // Explicit complete business-table set: no CASCADE, no identity/security/audit tables.
  db.execute("TRUNCATE "+String.join(",",TABLES.values())+",penny_demo_environment CONTINUE IDENTITY");
  audit(auth.getName(),"ENVIRONMENT_RESET",before);return state();
 }
 private void linkDemoOrders(){
  for(var line:db.queryForList("SELECT id,customer_id,reference,sold_on,status,notes FROM penny_sales WHERE order_id IS NULL ORDER BY id")){long order=db.queryForObject("INSERT INTO penny_sales_orders(customer_id,order_number,sold_on,status,notes) VALUES (?,?,?,?,?) RETURNING id",Long.class,line.get("customer_id"),line.get("reference")==null?"DEMO-"+line.get("id"):line.get("reference"),line.get("sold_on"),line.get("status"),line.get("notes"));db.update("UPDATE penny_sales SET order_id=? WHERE id=?",order,line.get("id"));}
 }
 @PostMapping("/demo") @Transactional public Map<String,Object> demo(@RequestBody DemoInput input,Authentication auth){
  require(auth);if(!Boolean.TRUE.equals(input.confirm()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Conferma il caricamento dei dati demo");
  lock();int version=scenarioVersion();if(version>=2)return state();
  if(version==1){
   db.queryForObject("SELECT set_config('penny.actor',?,true)",String.class,auth.getName());
   new ExpandedDemoScenarioSeeder(db).seed(auth.getName());
   db.update("UPDATE penny_demo_environment SET scenario_version=2 WHERE id=1");
   linkDemoOrders();audit(auth.getName(),"DEMO_DATA_EXPANDED",counts());return state();
  }
  db.queryForObject("SELECT set_config('penny.actor',?,true)",String.class,auth.getName());
  long[] customers=new long[12];
  String[] names={"Aurora Informatica","Studio Verdi","Officine Rossi","Hotel Lago","Farmacia Centro","Logistica Nord","Scuola Europa","Studio Bianchi","Alfa Retail","Giulia Riva","Paolo Ferrari","Delta Consulting"};
  for(int i=0;i<12;i++){
   customers[i]=db.queryForObject("INSERT INTO penny_customers(kind,name,email,phone,street,postal_code,city,notes,archived) VALUES (?,?,?,?,?,?,?,'Dati dimostrativi',?) RETURNING id",Long.class,(i==9||i==10)?"PERSON":"COMPANY","[DEMO] "+names[i],"demo"+i+"@example.invalid","045000"+String.format("%04d",i),"Via Esempio "+(i+1),i%2==0?"37100":"20100",i%2==0?"Verona":"Milano",i==11);
   long site=db.queryForObject("INSERT INTO penny_customer_sites(customer_id,name,street,city) VALUES (?,'[DEMO] Sede principale','Via Esempio 10','Verona') RETURNING id",Long.class,customers[i]);
   long contact=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name,email,role,is_primary) VALUES (?,? ,?,'Referente IT',true) RETURNING id",Long.class,customers[i],"[DEMO] Referente "+(i+1),"referente"+i+"@example.invalid");
   db.update("UPDATE penny_contact_customers SET site_id=? WHERE contact_id=? AND customer_id=?",site,contact,customers[i]);
   if(i==1)db.update("INSERT INTO penny_contact_customers(contact_id,customer_id,role) VALUES (?,?,'Consulente condiviso')",contact,customers[0]);
   if(i<8){
    db.queryForObject("INSERT INTO penny_workstations(customer_id,site_id,name,location) VALUES (?,?,'[DEMO] Postazione 1','Ufficio') RETURNING id",Long.class,customers[i],site);
    // Catalog is created once below; installations follow after every customer exists.
   }
  }
  String[] categories={"HARDWARE","SOFTWARE","SERVICE","TRAINING"};
  for(int i=0;i<4;i++){
   String origin=i==1?"INTERNAL":"THIRD_PARTY";
   String name="[DEMO] "+new String[]{"Notebook professionale","Penny Gestionale","Assistenza tecnica","Formazione utenti"}[i];
   long product=db.queryForObject("INSERT INTO penny_catalog_products(code,name,category,origin,manufacturer,supplier,price,description) VALUES (?,?,?,?,?,?,?,'Voce commerciale dimostrativa') RETURNING id",Long.class,"DEMO-"+categories[i],name,categories[i],origin,i==1?"Penny":"Produttore demo","Distributore demo",new java.math.BigDecimal(i==0?"1200.00":"150.00"));
   for(int c=0;c<8;c++){
    long sale=db.queryForObject("INSERT INTO penny_sales(customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price,reference) VALUES (?,?,?,?,?,?,current_date-30,10,150,?) RETURNING id",Long.class,customers[c],product,"DEMO-"+categories[i],name,origin,categories[i],"DEMO-ORD-"+i+"-"+c);
    if(i<2){
     long station=db.queryForObject("SELECT id FROM penny_workstations WHERE customer_id=? AND name='[DEMO] Postazione 1'",Long.class,customers[c]);
     long site=db.queryForObject("SELECT site_id FROM penny_workstations WHERE id=?",Long.class,station);
     int days=new int[]{-20,15,180,30,-5,365,7,90}[c];
     long installed=db.queryForObject("INSERT INTO penny_installed_products(customer_id,category,name,origin,catalog_product_id,workstation_id,site_id,manufacturer,supplier,quantity,activated_users,serial_number,license_key,installed_on,warranty_until,license_expires_on,status) VALUES (?,?,?,?,?,?,?,'Produttore demo','Distributore demo',?,?,?, ?,current_date-400,current_date+?,CASE WHEN ?='SOFTWARE' THEN current_date+? ELSE NULL END,?) RETURNING id",Long.class,customers[c],categories[i],name,origin,product,station,site,i==0?1:10,i==1?10:null,i==0?"DEMO-SERIAL-"+c:null,i==1?"DEMO-LIC-"+c:null,days,categories[i],days,c==4?"MAINTENANCE":"ACTIVE");
     db.update("INSERT INTO penny_sale_allocations(sale_id,installed_product_id,customer_id,catalog_product_id,quantity) VALUES (?,?,?,?,?)",sale,installed,customers[c],product,i==0?1:10);
    }
   }
  }
  // Pregresso owned by prospects: deliberately no catalog link, sale or promotion.
  for(int c=8;c<11;c++){
   long site=db.queryForObject("SELECT id FROM penny_customer_sites WHERE customer_id=? ORDER BY id LIMIT 1",Long.class,customers[c]);
   boolean software=c==9;
   db.update("INSERT INTO penny_installed_products(customer_id,site_id,category,name,origin,manufacturer,supplier,quantity,activated_users,serial_number,installed_on,warranty_until,license_expires_on,status,notes) VALUES (?,?,?,?,'THIRD_PARTY',?,'Acquisto pregresso del cliente',?,?,?,current_date-300,current_date+60,CASE WHEN ? THEN current_date+20 ELSE NULL END,?,'Censimento demo senza vendita')",customers[c],site,software?"SOFTWARE":"HARDWARE",software?"[DEMO] Licenze Office pregresse":"[DEMO] PC pregresso "+c,software?"Microsoft":"Dell",software?5:1,software?3:null,software?null:"DEMO-PRE-"+c,software,c==10?"RETIRED":"ACTIVE");
  }
  long trashed=db.queryForObject("INSERT INTO penny_customers(kind,name) VALUES ('COMPANY','[DEMO] Anagrafica nel cestino') RETURNING id",Long.class);
  db.update("UPDATE penny_customers SET deleted_at=now() WHERE id=?",trashed);
  db.update("INSERT INTO penny_trash_entries(entity_type,entity_id,label,deleted_by,deleted_at,expires_at) VALUES ('CUSTOMER',?,'[DEMO] Anagrafica nel cestino',?,now(),now()+interval '720 hours')",trashed,auth.getName());
  db.update("INSERT INTO penny_catalog_products(code,name,category,origin,active) VALUES ('DEMO-ARCHIVE','[DEMO] Modello fuori listino','HARDWARE','THIRD_PARTY',false)");
  new ExpandedDemoScenarioSeeder(db).seed(auth.getName());
  db.update("INSERT INTO penny_demo_environment(id,actor,scenario_version) VALUES (1,?,2)",auth.getName());linkDemoOrders();audit(auth.getName(),"DEMO_DATA_LOADED",counts());return state();
 }
}
