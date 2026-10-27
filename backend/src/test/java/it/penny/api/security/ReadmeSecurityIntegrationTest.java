package it.penny.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.penny.api.PennyApplication;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the README contracts with the real filter chain and real database. */
@SpringBootTest(classes=PennyApplication.class)
@EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class ReadmeSecurityIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){
  String url=System.getenv("PENNY_TEST_JDBC_URL");
  p.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema=readme_security");
  p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));
  p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));
  p.add("spring.flyway.schemas",()->"readme_security");
  p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
 }
 @Autowired WebApplicationContext context;
 @Autowired FilterChainProxy security;
 @Autowired JdbcTemplate db;
 @Autowired PasswordEncoder encoder;
 @Autowired ObjectMapper json;
 MockMvc mvc;
 String email;
 static final String PASSWORD="Readme-original-password-2026";
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
  db.execute("TRUNCATE penny_users CASCADE");
  email="readme-"+UUID.randomUUID()+"@test.invalid";
  db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'README',?,false)",email,encoder.encode(PASSWORD));
  db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code='ADMIN'",email);
 }
 record Csrf(Cookie cookie,String token){}
 Csrf csrf() throws Exception {
  var response=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
  return new Csrf(response.getCookie("XSRF-TOKEN"),json.readTree(response.getContentAsString()).get("token").asText());
 }
 MockHttpServletRequestBuilder secured(MockHttpServletRequestBuilder request,Csrf csrf){return request.cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token());}
 MockHttpSession login(String account,String password) throws Exception {
  Csrf csrf=csrf();
  var result=mvc.perform(secured(post("/api/auth/login").servletPath("/api/auth/login"),csrf).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",account,"password",password))))
    .andExpect(status().isOk()).andReturn();
  return (MockHttpSession)result.getRequest().getSession(false);
 }
 @Test void loginWithoutCsrfIs403AndAnonymousProtectedRequestIs401() throws Exception {
  mvc.perform(post("/api/auth/login").servletPath("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD))))
    .andExpect(status().isForbidden());
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/health")).andExpect(status().isOk());
 }
 @Test void bcryptLoginPersistsSessionAndLogoutInvalidatesIt() throws Exception {
  assertInstanceOf(BCryptPasswordEncoder.class,encoder);
  MockHttpSession session=login(email,PASSWORD);
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
  Csrf csrf=csrf();
  mvc.perform(secured(post("/api/auth/logout"),csrf).session(session)).andExpect(status().isNoContent());
  assertTrue(session.isInvalid());
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
 }
 @Test void forcedPasswordChangeBlocksBusinessAccessAndChangeRevokesSession() throws Exception {
  db.update("UPDATE penny_users SET must_change_password=true WHERE email=?",email);
  MockHttpSession session=login(email,PASSWORD);
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true));
  mvc.perform(get("/api/customers").session(session)).andExpect(status().isForbidden());
  String payload=json.writeValueAsString(Map.of("currentPassword",PASSWORD,"newPassword","Readme-new-password-2026"));
  mvc.perform(post("/api/auth/change-password").session(session).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
  mvc.perform(secured(post("/api/auth/change-password"),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
  assertTrue(session.isInvalid());
  MockHttpSession fresh=login(email,"Readme-new-password-2026");
  mvc.perform(get("/api/customers").session(fresh)).andExpect(status().isOk());
 }
 @Test void disablingUserRevokesAnAlreadyAuthenticatedSession() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  String other="target@test.invalid";
  db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'Target',?,false)",other,encoder.encode(PASSWORD));
  MockHttpSession target=login(other,PASSWORD);
  long id=db.queryForObject("SELECT id FROM penny_users WHERE email=?",Long.class,other);
  mvc.perform(secured(patch("/api/admin/users/"+id),csrf()).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
  mvc.perform(get("/api/auth/me").session(target)).andExpect(status().isUnauthorized());
  assertTrue(target.isInvalid());
 }
 @Test void resettingPasswordRevokesAnAlreadyAuthenticatedSession() throws Exception {
  MockHttpSession session=login(email,PASSWORD);
  String other="reset-target@test.invalid";
  db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,'Target',?,false)",other,encoder.encode(PASSWORD));
  MockHttpSession target=login(other,PASSWORD);
  long id=db.queryForObject("SELECT id FROM penny_users WHERE email=?",Long.class,other);
  mvc.perform(secured(post("/api/admin/users/"+id+"/reset-password"),csrf()).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.temporaryPassword").isString());
  mvc.perform(get("/api/auth/me").session(target)).andExpect(status().isUnauthorized());
  assertTrue(target.isInvalid());
 }
 @Test void sixthIncorrectLoginIsRateLimited() throws Exception {
  Csrf csrf=csrf();
  String payload=json.writeValueAsString(Map.of("email",email,"password","incorrect"));
  for(int i=0;i<5;i++)mvc.perform(secured(post("/api/auth/login").servletPath("/api/auth/login"),csrf).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
  mvc.perform(secured(post("/api/auth/login").servletPath("/api/auth/login"),csrf).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"));
 }
}
