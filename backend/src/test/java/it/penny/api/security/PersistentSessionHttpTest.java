package it.penny.api.security;

import it.penny.api.PennyApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=PennyApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="penny.trash.cleanup-enabled=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class PersistentSessionHttpTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=persistent_sessions");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"persistent_sessions");}
 @Autowired TestRestTemplate http; @Autowired JdbcTemplate db; @Autowired PasswordEncoder encoder; @Autowired JdbcIndexedSessionRepository sessions;
 String email,password="Image_session_test_123";
 @BeforeEach void user(){email=UUID.randomUUID()+"@test.invalid";db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'Test sessione',?,false)",email,encoder.encode(password));}
 String csrf(String cookie){var h=new HttpHeaders();if(cookie!=null)h.set("Cookie",cookie);var r=http.exchange("/api/auth/csrf",HttpMethod.GET,new HttpEntity<>(h),Map.class);assertEquals(HttpStatus.OK,r.getStatusCode());return (String)r.getBody().get("token");}
 String login(){
  String token=csrf(null);var h=new HttpHeaders();h.setContentType(MediaType.APPLICATION_JSON);h.set("Cookie","XSRF-TOKEN="+token);h.set("X-XSRF-TOKEN",token);
  var r=http.postForEntity("/api/auth/login",new HttpEntity<>(Map.of("email",email,"password",password),h),String.class);assertEquals(HttpStatus.OK,r.getStatusCode());
  String header=r.getHeaders().getOrEmpty("Set-Cookie").stream().filter(s->s.startsWith("PENNY_SESSION=")).findFirst().orElseThrow();
  assertTrue(header.contains("Max-Age=2592000"),header);assertTrue(header.contains("HttpOnly"));assertTrue(header.contains("SameSite=Lax"));return header.split(";",2)[0];
 }
 String id(String cookie){return new String(Base64.getDecoder().decode(cookie.substring(cookie.indexOf('=')+1)),StandardCharsets.UTF_8);}
 ResponseEntity<String> me(String cookie){var h=new HttpHeaders();h.set("Cookie",cookie);return http.exchange("/api/auth/me",HttpMethod.GET,new HttpEntity<>(h),String.class);}
 @Test void persistsForThirtyDaysAndLogoutRevokesTheStoredSession(){
  String cookie=login(),id=id(cookie);org.springframework.session.Session saved=sessions.findById(id);assertNotNull(saved);assertEquals(Duration.ofDays(30),saved.getMaxInactiveInterval());assertEquals(email,saved.<org.springframework.security.core.context.SecurityContext>getAttribute("SPRING_SECURITY_CONTEXT").getAuthentication().getName());
  assertTrue(saved.<Long>getAttribute("PENNY_SESSION_EXPIRES_AT")>System.currentTimeMillis()+Duration.ofDays(29).toMillis());assertEquals(HttpStatus.OK,me(cookie).getStatusCode());
  String token=csrf(cookie);var h=new HttpHeaders();h.set("Cookie",cookie+"; XSRF-TOKEN="+token);h.set("X-XSRF-TOKEN",token);
  assertEquals(HttpStatus.NO_CONTENT,http.postForEntity("/api/auth/logout",new HttpEntity<>(h),String.class).getStatusCode());
  assertNull(sessions.findById(id));assertEquals(HttpStatus.UNAUTHORIZED,me(cookie).getStatusCode());
 }
 @Test void absoluteExpiryAndAccountRevocationStillInvalidateLongSessions(){
  String cookie=login(),id=id(cookie);org.springframework.session.Session saved=sessions.findById(id);saved.setAttribute("PENNY_SESSION_EXPIRES_AT",System.currentTimeMillis()-1);((org.springframework.session.SessionRepository)sessions).save(saved);
  assertEquals(HttpStatus.UNAUTHORIZED,me(cookie).getStatusCode());assertNull(sessions.findById(id));
  cookie=login();db.update("UPDATE penny_users SET session_version=session_version+1 WHERE email=?",email);assertEquals(HttpStatus.UNAUTHORIZED,me(cookie).getStatusCode());
 }
 @Test void preservesExistingSupportForLongEmailAddresses(){
  String longer="long-"+UUID.randomUUID()+"@"+"d".repeat(60)+".example.invalid";
  db.update("UPDATE penny_users SET email=? WHERE email=?",longer,email);email=longer;
  String cookie=login();assertEquals(HttpStatus.OK,me(cookie).getStatusCode());assertNotNull(sessions.findById(id(cookie)));
 }
}
