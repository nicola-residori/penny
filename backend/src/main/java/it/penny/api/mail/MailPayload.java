package it.penny.api.mail;

import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record MailPayload(Long customerId,String mailbox,String itemId,String internetMessageId,String conversationId,
 String subject,String sender,List<String> recipients,Instant sentAt,String bodyText,String originalBase64,String originalKind,List<Attachment> attachments) {
 public record Attachment(String filename,String contentBase64,Boolean inlineImage){}
 public record Checked(MailPayload input,String key,String conversation,byte[] original,List<byte[]> files){}
 public static final int MAX_BYTES=20*1024*1024;
 public Checked checked(){
  if(customerId==null||customerId<=0||sentAt==null)throw bad("Cliente e data del messaggio obbligatori");
  text(mailbox,320,true);text(itemId,2000,true);text(internetMessageId,1000,false);text(conversationId,2000,false);
  text(subject,1000,false);text(sender,320,true);text(bodyText,1000000,false);
  if(recipients==null||recipients.size()>200)throw bad("Destinatari non validi");
  recipients.forEach(r->text(r,320,true));
  if(!Set.of("EML","MSG","GENERATED").contains(Objects.toString(originalKind,"")))throw bad("Formato originale non valido");
  var original=decode(originalBase64,MAX_BYTES);if(original.length==0)throw bad("Messaggio originale mancante");
  if(attachments==null||attachments.size()>100)throw bad("Troppi allegati");
  long size=original.length;var files=new ArrayList<byte[]>();
  for(var file:attachments){if(file==null)throw bad("Allegato non valido");text(file.filename,200,true);var bytes=decode(file.contentBase64,10*1024*1024);files.add(bytes);size+=bytes.length;}
  if(size>MAX_BYTES)throw bad("Messaggio e allegati: massimo 20 MB complessivi");
  String identity=internetMessageId!=null&&!internetMessageId.isBlank()?internetMessageId.trim():mailbox.toLowerCase(Locale.ROOT)+"\n"+itemId;
  String key=hash(identity);
  return new Checked(this,key,conversationId==null||conversationId.isBlank()?key:hash(mailbox.toLowerCase(Locale.ROOT)+"\n"+conversationId),original,files);
 }
 static void text(String value,int max,boolean required){if((required&&(value==null||value.isBlank()))||(value!=null&&(value.length()>max||value.indexOf('\0')>=0)))throw bad("Dati email mancanti o troppo lunghi");}
 static byte[] decode(String value,int max){try{if(value==null||value.length()>((max+2L)/3)*4)throw bad("Allegato troppo grande o mancante");var bytes=Base64.getDecoder().decode(value);if(bytes.length>max)throw bad("Allegato troppo grande");return bytes;}catch(IllegalArgumentException e){throw bad("Codifica allegato non valida");}}
 public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
