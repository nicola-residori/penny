package it.penny.api.mail;
import it.penny.api.PennyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=PennyApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="penny.trash.cleanup-enabled=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class MailHttpSecurityTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=outlook_http");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"outlook_http");}
 @Autowired TestRestTemplate http;@Autowired JdbcTemplate db;
 @Test void bearerIsConfinedToBridgeAndMutationStillRequiresCsrf(){
  db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES ('outlook-http@test.invalid','HTTP','unused',false) ON CONFLICT(email) DO NOTHING");
  db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email='outlook-http@test.invalid' AND r.code='ADMIN' ON CONFLICT DO NOTHING");
  String token="http-test-token";db.update("DELETE FROM penny_mail_connections WHERE token_hash=?",MailPayload.hash(token));db.update("INSERT INTO penny_mail_connections(user_id,token_hash,session_version) SELECT id,?,session_version FROM penny_users WHERE email='outlook-http@test.invalid'",MailPayload.hash(token));
  var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);var body="{\"emails\":[],\"q\":\"test\"}";
  assertEquals(HttpStatus.UNAUTHORIZED,http.postForEntity("/api/outlook/bridge/customers",new HttpEntity<>(body,h),String.class).getStatusCode());
  h.setBearerAuth(token);assertEquals(HttpStatus.OK,http.postForEntity("/api/outlook/bridge/customers",new HttpEntity<>(body,h),String.class).getStatusCode());
  assertEquals(HttpStatus.UNAUTHORIZED,http.exchange("/api/customers",HttpMethod.GET,new HttpEntity<>(h),String.class).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,http.postForEntity("/api/mail/connections",new HttpEntity<>("{}",h),String.class).getStatusCode());
  db.update("UPDATE penny_mail_connections SET revoked_at=now() WHERE token_hash=?",MailPayload.hash(token));assertEquals(HttpStatus.UNAUTHORIZED,http.postForEntity("/api/outlook/bridge/customers",new HttpEntity<>(body,h),String.class).getStatusCode());
 }
}
