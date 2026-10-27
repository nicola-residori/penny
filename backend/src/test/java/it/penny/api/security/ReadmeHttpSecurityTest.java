package it.penny.api.security;
import it.penny.api.PennyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=PennyApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="penny.trash.cleanup-enabled=false")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class ReadmeHttpSecurityTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=readme_http_security");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"readme_http_security");}
 @Autowired TestRestTemplate http;
 @Test void realServletErrorDispatchDoesNotTurnMissingCsrf403Into401(){
  var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
  var body=new HttpEntity<>("{\"email\":\"csrf-http@example.invalid\",\"password\":\"invalid-password\"}",headers);
  assertEquals(HttpStatus.FORBIDDEN,http.postForEntity("/api/auth/login",body,String.class).getStatusCode());
  assertEquals(HttpStatus.FORBIDDEN,http.postForEntity("/api/auth/change-password",new HttpEntity<>("{}",headers),String.class).getStatusCode());
  assertEquals(HttpStatus.UNAUTHORIZED,http.getForEntity("/api/customers",String.class).getStatusCode());
  assertEquals(HttpStatus.UNAUTHORIZED,http.getForEntity("/error",String.class).getStatusCode());
  assertEquals(HttpStatus.OK,http.getForEntity("/actuator/health",String.class).getStatusCode());
 }
}
