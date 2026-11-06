package it.penny.api.mail;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MailPayloadTest {
 MailPayload mail(String id,List<MailPayload.Attachment> files){return new MailPayload(1L,"me@example.org","outlook-id",id,"thread","Oggetto","sender@example.org",List.of("recipient@example.org"),Instant.now(),"<script>test</script>","ZW1s","EML",files);}
 @Test void internetIdentityIsStableAndKeepsExactBytes(){var a=mail("<unique@example.org>",List.of(new MailPayload.Attachment("offerta.pdf","AAEC",false))).checked();assertArrayEquals(new byte[]{0,1,2},a.files().getFirst());assertEquals(a.key(),mail("<unique@example.org>",List.of()).checked().key());assertNotEquals(a.key(),mail("<other@example.org>",List.of()).checked().key());}
 @Test void malformedBase64FailsRatherThanOmittingAttachment(){assertThrows(org.springframework.web.server.ResponseStatusException.class,()->mail("x",List.of(new MailPayload.Attachment("a","!bad",false))).checked());}
 @Test void overlargeFilesAndNullsFail(){assertThrows(org.springframework.web.server.ResponseStatusException.class,()->mail("x",List.of(new MailPayload.Attachment("a",Base64.getEncoder().encodeToString(new byte[10485761]),false))).checked());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->mail("x",null).checked());}
}
