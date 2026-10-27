package it.penny.api.admin;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;

/** Additive version-two examples. Called only under the controller's transaction/marker lock. */
final class ExpandedDemoScenarioSeeder {
 private final JdbcTemplate db;
 private final long[] customers=new long[50];
 private final long[][] sites=new long[50][];
 private final long[][] products=new long[4][10];
 private final long[][] installed=new long[30][2];
 private long trashContactId;
 private static final String[] CATEGORIES={"HARDWARE","SOFTWARE","SERVICE","TRAINING"};
 private static final String[] SITE_TYPES={"[\"LEGAL\",\"ADMINISTRATIVE\"]","[\"OPERATIONAL\"]","[\"OPERATIONAL\",\"WAREHOUSE\"]","[\"COMMERCIAL\"]","[\"WAREHOUSE\"]"};
 private static final String[] PREFIXES={"HW","SW","SVC","TRN"};
 ExpandedDemoScenarioSeeder(JdbcTemplate db){this.db=db;}
 void seed(String actor){
  customersAndSites();contacts();catalog();installations();sales();
  // Archive only after historical assignments are established.
  for(int category=0;category<4;category++)for(int item=8;item<10;item++)
   db.update("UPDATE penny_catalog_products SET active=false WHERE id=?",products[category][item]);
  db.update("UPDATE penny_workstations SET active=false WHERE customer_id=? AND name='[DEMO] E2 Gruppo archivio'",customers[2]);
  db.update("UPDATE penny_customers SET archived=true WHERE id IN (?,?)",customers[29],customers[47]);
  trash(actor);
 }
 private void customersAndSites(){
  for(int i=0;i<50;i++){
   String label=i==0?"Gruppo Atlas":i==48?"Cliente autonomo nel cestino":i==49?"Cliente condiviso nel cestino":(i>=40||i==4||i==14?"Privato ":"Azienda ")+String.format("%02d",i+1);
   customers[i]=db.queryForObject("INSERT INTO penny_customers(kind,name,email,phone,street,postal_code,city,notes,archived) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,
    (i>=40&&i<48)||i==4||i==14?"PERSON":"COMPANY","[DEMO] E2 "+label,"e2.anagrafica"+(i+1)+"@example.invalid","045200"+String.format("%04d",i),"Via Scenario "+(i+1),"37100",i%2==0?"Verona":"Milano","Demo estesa: sedi multiple, contatti condivisi e casi commerciali",false);
   // Two trash-only examples intentionally have neither sites nor operational records.
   int count=i>=48?0:i==0?5:2+i%4;sites[i]=new long[count];
   for(int n=0;n<count;n++)sites[i][n]=db.queryForObject("INSERT INTO penny_customer_sites(customer_id,name,street,city,notes,site_types) VALUES (?,?,?,?,?,?::jsonb) RETURNING id",Long.class,customers[i],"[DEMO] E2 Sede "+(n+1),"Via Filiale "+(i+1)+"/"+(n+1),n%2==0?"Verona":"Vicenza","Sede dimostrativa",SITE_TYPES[n]);
   if(i<30)db.update("INSERT INTO penny_workstations(customer_id,site_id,name,location) VALUES (?,?,?,?)",customers[i],sites[i][0],i==2?"[DEMO] E2 Gruppo archivio":"[DEMO] E2 Gruppo operativo","Area "+(i%5+1));
  }
 }
 private void contacts(){
  for(int i=0;i<80;i++){
   int anchor=i<20?i:i%48;
   long contact=db.queryForObject("INSERT INTO penny_customer_contacts(customer_id,full_name,email,phone,role,is_primary) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,customers[anchor],"[DEMO] E2 "+(i<20?"Referente condiviso ":"Contatto ")+String.format("%02d",i+1),"e2.contatto"+(i+1)+"@example.invalid","340200"+String.format("%04d",i),i%2==0?"Referente IT":"Amministrazione",i%2==0);
   if(i==79)trashContactId=contact;
   db.update("UPDATE penny_contact_customers SET site_id=? WHERE contact_id=? AND customer_id=?",i%5==0?null:sites[anchor][0],contact,customers[anchor]);
   if(i<20)for(int link=1;link<2+i%3;link++){
    int target=(anchor+link+10)%48;
    db.update("INSERT INTO penny_contact_customers(contact_id,customer_id,site_id,role,is_primary) VALUES (?,?,?,?,?)",contact,customers[target],link==1?sites[target][1]:null,link==1?"Consulente esterno":"Responsabile acquisti",link==2);
   }
   // Identity survives on the live anchor when the other customer enters trash.
   if(i==0)db.update("INSERT INTO penny_contact_customers(contact_id,customer_id,role) VALUES (?,?,'Referente condiviso da ripristinare')",contact,customers[49]);
  }
 }
 private void catalog(){
  for(int category=0;category<4;category++)for(int item=0;item<10;item++){
   String origin=origin(item);String code=code(category,item);
   products[category][item]=db.queryForObject("INSERT INTO penny_catalog_products(code,name,category,origin,manufacturer,model,supplier,price,description) VALUES (?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,code,"[DEMO] E2 "+CATEGORIES[category]+" "+String.format("%02d",item+1),CATEGORIES[category],origin,origin.equals("INTERNAL")?"Penny Lab":"Produttore E2 "+item,"Modello "+(item+1),"Distributore E2 "+(item%3+1),item%3==0?null:item%3==1?BigDecimal.ZERO:new BigDecimal("249.90"),"Offerta demo estesa; formazione e servizi non implicano erogazione operativa");
  }
 }
 private String origin(int item){return item%2==0?"INTERNAL":"THIRD_PARTY";}
 private String code(int category,int item){return "E2-"+PREFIXES[category]+"-"+String.format("%02d",item+1);}
 private void installations(){
  // 110 on Atlas, plus two catalog-linked installations for each of the other 29 buyers.
  for(int customer=0;customer<30;customer++)for(int n=0;n<(customer==0?110:2);n++){
   int category=n%2,item=customer%10;String status=n>=2&&n%13==0?"RETIRED":n%7==0?"MAINTENANCE":"ACTIVE";
   long id=installation(customer,category,products[category][item],origin(item),"Parco "+String.format("%02d",customer+1)+" / "+String.format("%03d",n+1),"E2-SERIAL-"+customer+"-"+n,status,n%4==0?null:category==0?1:10,category==1?(n%3==0?null:n%3==1?0:10):null,n%5==0?-20:n%5==1?7:n%5==2?30:365,n);
   if(n<2)installed[customer][category]=id;
  }
  // Historical SERVICE is supported, but TRAINING is deliberately never installed.
  for(int n=0;n<3;n++)installation(30+n,2,null,"THIRD_PARTY","Servizio legacy "+(n+1),"E2-LEGACY-"+n,"ACTIVE",null,null,90,n);
  // Prospect inventory does not manufacture sales or CUSTOMER status.
  for(int n=0;n<6;n++)installation(34+n,n%2,null,"THIRD_PARTY","Pregresso prospect "+(n+1),"E2-PREGRESSO-"+n,n==5?"RETIRED":n==4?"MAINTENANCE":"ACTIVE",n%2==0?null:5,n%2==0?null:3,n%3==0?-10:n%3==1?15:180,n);
 }
 private long installation(int customer,int category,Long product,String origin,String label,String serial,String status,Integer quantity,Integer users,int days,int index){
  Long site=index%7==6?null:sites[customer][index%sites[customer].length];
  Long group=customer<30&&site!=null&&site==sites[customer][0]?db.queryForObject("SELECT id FROM penny_workstations WHERE customer_id=? ORDER BY id LIMIT 1",Long.class,customers[customer]):null;
  return db.queryForObject("INSERT INTO penny_installed_products(customer_id,site_id,workstation_id,category,name,origin,catalog_product_id,manufacturer,supplier,model,serial_number,license_key,quantity,activated_users,installed_on,warranty_until,license_expires_on,status,notes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,current_date-500,CASE WHEN ?='HARDWARE' THEN current_date+? ELSE NULL END,CASE WHEN ?='SOFTWARE' THEN current_date+? ELSE NULL END,?,?) RETURNING id",Long.class,
   customers[customer],site,group,CATEGORIES[category],"[DEMO] E2 "+label,origin,product,origin.equals("INTERNAL")?"Penny Lab":"Produttore esterno E2","Fornitore distinto E2","Serie "+(index%5+1),serial,category==1?"E2-LIC-"+serial:null,quantity,users,CATEGORIES[category],days,CATEGORIES[category],days,status,product==null?"Pregresso del cliente: nessuna vendita generata":"Installazione collegata al catalogo demo");
 }
 private void sales(){
  for(int customer=0;customer<30;customer++)for(int category=0;category<4;category++){
   int item=category==3&&customer%10==9?8:customer%10;int mode=customer%4;
   long sale=db.queryForObject("INSERT INTO penny_sales(customer_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price,reference,notes) VALUES (?,?,?,?,?,?,current_date-?,10,?,?,?) RETURNING id",Long.class,customers[customer],products[category][item],code(category,item),"[DEMO] E2 "+CATEGORIES[category]+" "+String.format("%02d",item+1),origin(item),CATEGORIES[category],customer*3+category,new BigDecimal("149.50"),"E2-ORD-"+String.format("%02d",customer+1)+"-"+PREFIXES[category],"Scenario "+(mode==0?"assegnazione completa":mode==1?"assegnazione parziale":mode==2?"disponibilità non assegnata":"annullamento motivato"));
   if(mode==3)db.update("UPDATE penny_sales SET status='CANCELLED',cancellation_reason='[DEMO] E2 Ordine duplicato dal cliente',cancelled_at=now() WHERE id=?",sale);
   else if(category<2&&(mode==0||mode==1))db.update("INSERT INTO penny_sale_allocations(sale_id,installed_product_id,customer_id,catalog_product_id,quantity) VALUES (?,?,?,?,?)",sale,installed[customer][category],customers[customer],products[category][item],mode==0?10:4);
  }
 }
 private void trash(String actor){
  trash("CUSTOMER","penny_customers",customers[48],"[DEMO] E2 Cliente autonomo nel cestino",7,actor);
  trash("CUSTOMER","penny_customers",customers[49],"[DEMO] E2 Cliente condiviso nel cestino",15,actor);
  trash("CONTACT","penny_customer_contacts",trashContactId,"[DEMO] E2 Contatto 80",30,actor);
  // Reserve the final TRAINING item as an unused, recoverable catalog example.
  long catalog=products[3][9];
  trash("CATALOG_PRODUCT","penny_catalog_products",catalog,"[DEMO] E2 TRAINING 10",15,actor);
  long installation=installation(46,0,null,"THIRD_PARTY","Installazione nel cestino","E2-TRASH-INSTALL","RETIRED",null,null,90,0);
  trash("INSTALLED_PRODUCT","penny_installed_products",installation,"[DEMO] E2 Installazione nel cestino",7,actor);
  long group=db.queryForObject("INSERT INTO penny_workstations(customer_id,site_id,name,location) VALUES (?,?,'[DEMO] E2 Gruppo vuoto nel cestino','Magazzino') RETURNING id",Long.class,customers[46],sites[46][0]);
  trash("WORKSTATION","penny_workstations",group,"[DEMO] E2 Gruppo vuoto nel cestino",30,actor);
 }
 private void trash(String type,String table,long id,String label,int remaining,String actor){
  db.update("UPDATE "+table+" SET deleted_at=now()-(30-?)*interval '1 day' WHERE id=?",remaining,id);
  db.update("INSERT INTO penny_trash_entries(entity_type,entity_id,label,deleted_by,deleted_at,expires_at) SELECT ?,?,?,?,deleted_at,deleted_at+interval '720 hours' FROM "+table+" WHERE id=?",type,id,label,actor,id);
 }
}
