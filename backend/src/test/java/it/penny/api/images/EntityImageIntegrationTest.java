package it.penny.api.images;

import it.penny.api.PennyApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=PennyApplication.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PENNY_TEST_JDBC_URL",matches=".+")
class EntityImageIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("PENNY_TEST_JDBC_URL")+"?currentSchema=entity_images");p.add("spring.datasource.username",()->System.getenv("PENNY_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("PENNY_TEST_DB_PASSWORD"));p.add("spring.flyway.schemas",()->"entity_images");}
 @Autowired JdbcTemplate db; @Autowired WebApplicationContext context; MockMvc mvc;
 final UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken("image-admin@test.invalid","unused"),tech=new UsernamePasswordAuthenticationToken("image-tech@test.invalid","unused"),none=new UsernamePasswordAuthenticationToken("image-none@test.invalid","unused");
 final byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aNBkAAAAASUVORK5CYII=");
 @BeforeEach void setup(){
  mvc=MockMvcBuilders.webAppContextSetup(context).build();db.execute("TRUNCATE penny_customers,penny_catalog_products,penny_users CASCADE");db.execute("TRUNCATE penny_trash_entries");
  for(String role:new String[]{"ADMIN","TECH","NONE"}){String email="image-"+role.toLowerCase()+"@test.invalid";db.update("INSERT INTO penny_users(email,full_name,password_hash,must_change_password) VALUES (?,?,'unused',false)",email,role);db.update("INSERT INTO penny_user_roles SELECT u.id,r.id FROM penny_users u CROSS JOIN penny_roles r WHERE u.email=? AND r.code=?",email,role);}
  db.update("INSERT INTO penny_customers(id,kind,name) VALUES (1,'COMPANY','Alfa')");
  db.update("INSERT INTO penny_customer_contacts(id,customer_id,full_name) VALUES (1,1,'Anna')");
  db.update("INSERT INTO penny_catalog_products(id,code,name,category,origin,image_data,image_mime) VALUES (1,'IMG','Hardware','HARDWARE','INTERNAL',?,'image/png')",png);
  db.update("INSERT INTO penny_installed_products(id,customer_id,catalog_product_id,name,category,origin) VALUES (1,1,1,'Server','HARDWARE','INTERNAL')");
 }
 @Test void customerImageRespectsPermissionsAndSupportsRemoval()throws Exception{
  var file=new MockMultipartFile("file","logo.png","image/png",png);
  mvc.perform(multipart("/api/entity-images/customers/1").file(file).principal(tech)).andExpect(status().isForbidden());
  mvc.perform(multipart("/api/entity-images/customers/1").file(file).principal(admin)).andExpect(jsonPath("$.hasImage").value(true));
  mvc.perform(get("/api/entity-images/customers/1").principal(none)).andExpect(status().isForbidden());
  mvc.perform(get("/api/entity-images/customers/1").principal(tech)).andExpect(content().bytes(png)).andExpect(header().string("Cache-Control","private, no-store"));
  mvc.perform(get("/api/customers/1").principal(admin)).andExpect(jsonPath("$.hasImage").value(true));
  mvc.perform(delete("/api/entity-images/customers/1").principal(admin)).andExpect(jsonPath("$.hasImage").value(false));
  mvc.perform(get("/api/entity-images/customers/1").principal(admin)).andExpect(status().isNotFound());
 }
 @Test void installedImageInheritsCatalogAndRestoresItAfterOverrideRemoval()throws Exception{
  mvc.perform(get("/api/entity-images/installed-products/1").principal(tech)).andExpect(content().bytes(png));
  byte[] jpeg={(byte)255,(byte)216,(byte)255,0};
  mvc.perform(multipart("/api/entity-images/installed-products/1").file(new MockMultipartFile("file","custom.jpg","image/jpeg",jpeg)).principal(tech)).andExpect(jsonPath("$.hasOwnImage").value(true));
  mvc.perform(get("/api/entity-images/installed-products/1").principal(tech)).andExpect(content().bytes(jpeg));
  mvc.perform(delete("/api/entity-images/installed-products/1").principal(tech)).andExpect(jsonPath("$.hasOwnImage").value(false)).andExpect(jsonPath("$.hasImage").value(true));
  mvc.perform(get("/api/entity-images/installed-products/1").principal(tech)).andExpect(content().bytes(png));
 }
 @Test void imageFollowsTrashAndRestoreLifecycle()throws Exception{
  mvc.perform(multipart("/api/entity-images/contacts/1").file(new MockMultipartFile("file","photo.png","image/png",png)).principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/contacts/1").principal(tech)).andExpect(jsonPath("$.hasImage").value(true));
  mvc.perform(delete("/api/trash/CONTACT/1").principal(admin)).andExpect(status().isOk());
  mvc.perform(delete("/api/trash/INSTALLED_PRODUCT/1").principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/entity-images/contacts/1").principal(admin)).andExpect(status().isNotFound());
  mvc.perform(get("/api/entity-images/installed-products/1").principal(tech)).andExpect(status().isNotFound());
  long entry=db.queryForObject("SELECT id FROM penny_trash_entries WHERE entity_type='CONTACT' AND entity_id=1",Long.class);
  mvc.perform(post("/api/trash/"+entry+"/restore").principal(admin)).andExpect(status().isOk());
  mvc.perform(get("/api/entity-images/contacts/1").principal(tech)).andExpect(content().bytes(png));
 }
 @Test void rejectsScriptFilesOversizeAndUnknownResourceKinds()throws Exception{
  mvc.perform(multipart("/api/entity-images/customers/1").file(new MockMultipartFile("file","logo.png","image/png","<svg onload='x'/>".getBytes())).principal(admin)).andExpect(status().isBadRequest());
  mvc.perform(multipart("/api/entity-images/customers/1").file(new MockMultipartFile("file","large.png","image/png",new byte[5242881])).principal(admin)).andExpect(status().isPayloadTooLarge());
  mvc.perform(get("/api/entity-images/users/1").principal(admin)).andExpect(status().isNotFound());
  assertNull(db.queryForObject("SELECT image_data FROM penny_customers WHERE id=1",byte[].class));
 }
}
