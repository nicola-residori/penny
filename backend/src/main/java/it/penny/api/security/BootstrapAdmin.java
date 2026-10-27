package it.penny.api.security;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
@Component
public class BootstrapAdmin implements ApplicationRunner {
 private final PennyUserRepository users; private final PasswordEncoder encoder; private final JdbcTemplate db;
 public BootstrapAdmin(PennyUserRepository users,PasswordEncoder encoder,JdbcTemplate db){this.users=users;this.encoder=encoder;this.db=db;}
 @Override public void run(ApplicationArguments args){
  String email=System.getenv("PENNY_BOOTSTRAP_EMAIL");
  String password=System.getenv("PENNY_BOOTSTRAP_PASSWORD");
  if(users.count()!=0)return;
  if(email==null||email.isBlank()||password==null||password.length()<12)
   throw new IllegalStateException("Primo avvio: impostare PENNY_BOOTSTRAP_EMAIL e PENNY_BOOTSTRAP_PASSWORD (almeno 12 caratteri)");
  PennyUser u=new PennyUser();u.email=email.trim().toLowerCase();u.fullName="Amministratore";
  u.passwordHash=encoder.encode(password);u.mustChangePassword=true;users.saveAndFlush(u);
  db.update("INSERT INTO penny_user_roles(user_id,role_id) SELECT ?,id FROM penny_roles WHERE code='ADMIN' ON CONFLICT DO NOTHING",u.id);
 }
}
