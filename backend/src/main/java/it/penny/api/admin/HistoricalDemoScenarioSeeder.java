package it.penny.api.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;

/** Version three is additive and runs inside the controller's transaction and advisory lock. */
final class HistoricalDemoScenarioSeeder {
 private final JdbcTemplate db;
 private final long[] customers=new long[48];
 private final long[] products=new long[4];
 private final Long[] types=new Long[4];
 private static final String[] CATEGORIES={"HARDWARE","SOFTWARE","TRAINING","SERVICE"};
 private static final String[] KINDS={"HARDWARE","SOFTWARE","TRAINING","CONTRACT"};
 private static final String[] NAMES={"Workstation professionale","Licenza CAD annuale","Corso progettazione","Contratto assistenza"};
 HistoricalDemoScenarioSeeder(JdbcTemplate db){this.db=db;}
 void seed(String actor){
  LocalDate end=db.queryForObject("SELECT current_date",LocalDate.class);
  LocalDate start=end.minusYears(2).plusDays(1);
  for(int i=0;i<48;i++){
   LocalDate joined=start.withDayOfMonth(1).plusMonths(i/2);
   if(joined.isBefore(start))joined=start;
   customers[i]=db.queryForObject("INSERT INTO penny_customers(kind,name,email,city,notes,created_at) VALUES (?,?,?,?,?,?::date) RETURNING id",Long.class,i%9==0?"PERSON":"COMPANY","[DEMO] E3 Cliente storico "+String.format("%02d",i+1),"e3.cliente"+(i+1)+"@example.invalid",i%2==0?"Verona":"Milano","[DEMO] Storico biennale; primo acquisto nel mese di ingresso",joined);
   db.update("INSERT INTO penny_customer_sites(customer_id,name,city) VALUES (?,'[DEMO] E3 Sede principale',?)",customers[i],i%2==0?"Verona":"Milano");
   db.update("INSERT INTO penny_customer_contacts(customer_id,full_name,email,role,is_primary) VALUES (?,?,?,'Referente acquisti',true)",customers[i],"[DEMO] E3 Referente "+(i+1),"e3.referente"+(i+1)+"@example.invalid");
  }
  for(int c=0;c<4;c++){
   long category=db.queryForObject("SELECT id FROM penny_product_categories WHERE code=?",Long.class,KINDS[c]);
   var available=db.queryForList("SELECT id FROM penny_product_types WHERE category_id=? AND NOT obsolete ORDER BY id LIMIT 1",Long.class,category);
   types[c]=available.isEmpty()?null:available.get(0);
   products[c]=db.queryForObject("INSERT INTO penny_catalog_products(code,name,category,category_id,product_type_id,origin,price,unit,margin_management,description) VALUES (?,?,?,?,?,'THIRD_PARTY',?,'unità','NONE','[DEMO] Storico biennale commerciale') RETURNING id",Long.class,"E3-"+KINDS[c],"[DEMO] E3 "+NAMES[c],CATEGORIES[c],category,types[c],new BigDecimal(900+c*125));
  }
  int day=0;
  for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1),day++){
   int month=(int)ChronoUnit.MONTHS.between(start.withDayOfMonth(1),date.withDayOfMonth(1));
   // Guarantee two first purchases every month, then rotate only among customers already acquired.
   if(month<24&&(date.equals(start)||date.getDayOfMonth()==1))for(int n=0;n<2;n++)sale(date,month*2+n,(month+n)%4,1,day,n,false);
   int eligible=Math.min(48,(month+1)*2);
   int lines=date.getMonthValue()==8?2:date.getMonthValue()>=9?4:3;
   for(int line=0;line<lines;line++)sale(date,(day*7+line*11)%eligible,(day+line)%4,1+(day+line)%5,day,line+2,day%29==0&&line==1);
   long customer=customers[(day*7)%eligible];
   String kind=day%5==0?"TRAINING":day%5==1?"CONTRACT":day%5==2?"DEVELOPMENT":day%5==3?"TECHNICAL":"COMMERCIAL";
   String status=kind.equals("CONTRACT")?(date.plusYears(1).isBefore(end)?"EXPIRED":"ACTIVE"):"COMPLETED";
   Long type=kind.equals("TRAINING")?types[2]:kind.equals("CONTRACT")?types[3]:null;
   long record=db.queryForObject("INSERT INTO penny_customer_records(customer_id,kind,name,product_type_id,starts_on,expires_on,status,notes,details,created_at) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?::date) RETURNING id",Long.class,customer,kind,"[DEMO] E3 "+kind+" "+date,type,date,kind.equals("CONTRACT")?date.plusYears(1):date,status,"[DEMO] Attività storica biennale",details(kind,date,day),date);
   if(kind.equals("TECHNICAL")||kind.equals("DEVELOPMENT"))db.update("INSERT INTO penny_record_tasks(record_id,name,progress,starts_on,ends_on) VALUES (?,'[DEMO] E3 Verifica e consegna',100,?,?)",record,date,date);
   if(kind.equals("TECHNICAL")){
    long intervention=db.queryForObject("INSERT INTO penny_customer_records(customer_id,kind,name,starts_on,expires_on,status,notes,details,created_at) VALUES (?,'INTERVENTION',?,?,?,'COMPLETED',?,?::jsonb,?::date) RETURNING id",Long.class,customer,"[DEMO] E3 Intervento "+date,date,date,"[DEMO] Intervento collegato al progetto tecnico",details("INTERVENTION",date,day),date);
    db.update("INSERT INTO penny_record_interventions(record_id,intervention_id) VALUES (?,?)",record,intervention);
   }
   if(kind.equals("TRAINING"))db.update("INSERT INTO penny_record_participants(record_id,name) VALUES (?,'[DEMO] E3 Partecipante cliente')",record);
  }
 }
 private String details(String kind,LocalDate date,int day){
  var d=JsonNodeFactory.instance.objectNode();
  if(kind.equals("CONTRACT")){
   int mode=(day/5)%4;
   d.put("contractType",mode==0?"PHONE":mode==1?"PHONE_UPDATES":mode==2?"HOURLY":"GENERAL");
   d.put("assistanceBase",mode==2?"HOURLY":"GENERAL");
   d.put("supplyChannel","INTERNAL_SALE");d.put("supplier","[DEMO] E3 Assistenza");
   d.put("soldOn",date.toString());d.put("price",mode==2?"1200.00":"720.00");
   d.put("seniorRate","90.00");d.put("juniorRate","65.00");d.put("travelCost","35.00");
   d.put("phoneAmount",mode<2?"480.00":"0.00");d.put("updatesAmount",mode==1?"240.00":"0.00");
   d.put("purchaseCost","240.00");d.put("collaboratorCost","180.00");
   d.put("description","[DEMO] Copertura e condizioni contrattuali dimostrative");
   if(mode==2){d.put("totalHours","20");d.put("remainingHours",day<365?"0":"12");}
  } else if(kind.equals("TRAINING")){
   d.put("venue",day%2==0?"ONLINE":"CUSTOMER_SITE");d.put("courseType","STANDARD");
   d.put("hours","8");d.put("teacherText","[DEMO] Docente esterno");d.put("soldOn",date.toString());d.put("price","640.00");
  } else if(kind.equals("INTERVENTION")){
   d.put("activityOn",date.toString());d.put("interventionType",day%2==0?"CONFIGURATION":"TROUBLESHOOTING");
   d.put("venue",day%2==0?"ONLINE":"CUSTOMER_SITE");d.put("startTime","09:00");d.put("endTime","11:30");
   d.put("responsibleText","[DEMO] Tecnico esterno");d.put("description","[DEMO] Configurazione e verifica operativa");
  } else {
   d.put("description","[DEMO] Attività e consegna storica");d.put("progress",100);
   d.put("closedOn",date.toString());d.put("responsibleText","[DEMO] Responsabile progetto");
   if(kind.equals("DEVELOPMENT")){d.put("developmentType","INTERNAL");d.put("estimatedHours","12");}
   if(kind.equals("COMMERCIAL")){d.put("activityType","CALL");d.put("origin","CUSTOMER_REQUEST");}
  }
  return d.toString();
 }
 private void sale(LocalDate date,int customer,int category,int quantity,int day,int line,boolean cancelled){
  String reference="E3-"+date+"-"+line;
  String status=cancelled?"CANCELLED":"CONFIRMED";
  long order=db.queryForObject("INSERT INTO penny_sales_orders(customer_id,order_number,sold_on,status,notes,created_at) VALUES (?,?,?,?,?,?::date) RETURNING id",Long.class,customers[customer],reference,date,status,"[DEMO] E3 Ordine storico",date);
  // Seasonality and year growth make comparisons visibly different without randomness.
  BigDecimal price=BigDecimal.valueOf((300+category*240+(day%17)*13)*(date.getMonthValue()==8?80:date.getMonthValue()>=9?120:100)*(day>=365?118L:100L),4).setScale(2,RoundingMode.HALF_UP);
  db.update("INSERT INTO penny_sales(customer_id,order_id,catalog_product_id,product_code,product_name,origin,category,sold_on,quantity,unit_price,reference,status,cancellation_reason,cancelled_at,notes,created_at) VALUES (?,?,?,?,?,'THIRD_PARTY',?,?,?,?,?,?,?,?::date,?,?::date)",customers[customer],order,products[category],"E3-"+KINDS[category],"[DEMO] E3 "+NAMES[category],CATEGORIES[category],date,quantity,price,reference,status,cancelled?"[DEMO] E3 Richiesta annullata dal cliente":null,cancelled?date:null,"[DEMO] Storico biennale",date);
 }
}
