package it.penny.api.products;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

/** Typed validation of optional installation details; omissions preserve existing values. */
final class InstalledBusiness {
 private static final ObjectMapper JSON=new ObjectMapper();
 private static final Set<String> FIELDS=Set.of("usageRelease","releaseUpdatedOn","soldRelease","licenseType","softwareStatus","saleSource","soldOn","salePrice","workstationReference","saleType","expiresOn","warrantyYears","purchasePrice","configuration","hardwareStatus");
 static ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
 static ObjectNode parse(Object stored){try{return stored==null?JSON.createObjectNode():(ObjectNode)JSON.readTree(stored.toString());}catch(Exception e){throw new IllegalStateException("Invalid installed business details",e);}}
 static String text(ObjectNode node,String key){return node.path(key).isNull()||node.path(key).isMissingNode()?null:node.path(key).asText();}
 static LocalDate date(ObjectNode node,String key){String value=text(node,key);try{return value==null?null:LocalDate.parse(value);}catch(Exception e){throw bad("Data non valida: "+key);}}
 static ObjectNode validate(JsonNode input,Object previous,String category,LocalDate licenseExpiry,boolean linked){
  ObjectNode out=parse(previous);
  if(input!=null){if(input.isNull())out=JSON.createObjectNode();else{if(!input.isObject())throw bad("Dettagli non validi");var fields=input.fields();while(fields.hasNext()){var entry=fields.next();String key=entry.getKey();var value=entry.getValue();if(!FIELDS.contains(key))throw bad("Campo sconosciuto: "+key);if(value.isNull()){out.putNull(key);continue;}if(key.equals("salePrice")||key.equals("purchasePrice")){out.put(key,CatalogProductController.price(value).toPlainString());continue;}if(key.equals("warrantyYears")){if(!value.isIntegralNumber()&&!value.isTextual())throw bad("Anni di garanzia non validi");try{int years=Integer.parseInt(value.asText());if(years<0||years>100)throw bad("Anni di garanzia non validi");out.put(key,years);}catch(NumberFormatException e){throw bad("Anni di garanzia non validi");}}else{if(!value.isTextual())throw bad("Campo non valido: "+key);String v=value.textValue().trim();if(v.length()>(key.equals("configuration")?10000:240))throw bad("Campo troppo lungo: "+key);if(v.isEmpty())out.putNull(key);else out.put(key,v);}}}}
  choice(out,"licenseType",Set.of("PERPETUAL","MONTHLY","QUARTERLY","ANNUAL","THREE_YEAR","TOKEN"));
  choice(out,"softwareStatus",Set.of("ACTIVE","EXPIRING","EXPIRED","SUSPENDED","CANCELLED","OTHER_RESELLER","TO_INSTALL"));
  choice(out,"saleSource",Set.of("OUR_SALE","THIRD_PARTY"));choice(out,"saleType",Set.of("SALE","RENTAL","DEMO"));choice(out,"hardwareStatus",Set.of("IN_USE","OBSOLETE"));
  for(String key:List.of("releaseUpdatedOn","soldOn","expiresOn"))date(out,key);
  for(String key:List.of("salePrice","purchasePrice")){var value=out.get(key);if(value!=null&&!value.isNull())out.put(key,CatalogProductController.price(value).toPlainString());}
  if("PERPETUAL".equals(text(out,"licenseType"))&&licenseExpiry!=null)throw bad("La licenza perpetua non ha scadenza");
  if("SALE".equals(text(out,"saleType"))&&date(out,"expiresOn")!=null)throw bad("La vendita hardware non ha scadenza di noleggio");
  if(!"OUR_SALE".equals(text(out,"saleSource"))&&(date(out,"soldOn")!=null||text(out,"salePrice")!=null))throw bad("Data e prezzo manuali richiedono vendita nostra");
  if(linked&&input!=null&&input.isObject()&&((input.hasNonNull("soldOn")&&!Objects.equals(input.get("soldOn"),parse(previous).get("soldOn")))||(input.hasNonNull("salePrice")&&!Objects.equals(input.get("salePrice"),parse(previous).get("salePrice")))))throw bad("Data e prezzo sono determinati dalle vendite collegate");
  if(linked){out.put("saleSource","OUR_SALE");out.putNull("soldOn");out.putNull("salePrice");}
  if(!category.equals("SOFTWARE")&&(text(out,"licenseType")!=null||text(out,"softwareStatus")!=null||text(out,"usageRelease")!=null||text(out,"releaseUpdatedOn")!=null||text(out,"soldRelease")!=null))throw bad("Campi software incompatibili con la categoria");
  if(!category.equals("HARDWARE")&&(text(out,"saleType")!=null||text(out,"hardwareStatus")!=null||text(out,"warrantyYears")!=null||text(out,"purchasePrice")!=null||text(out,"configuration")!=null||text(out,"expiresOn")!=null))throw bad("Campi hardware incompatibili con la categoria");
  return out;
 }
 private static void choice(ObjectNode node,String key,Set<String> choices){String value=text(node,key);if(value!=null&&!choices.contains(value))throw bad("Valore non valido: "+key);}
}
