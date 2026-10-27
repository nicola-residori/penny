package it.penny.api.security;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;
@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AuthenticationManager manager;
 private final SecurityContextRepository contexts;
 private final PennyUserRepository users;
 private final PasswordEncoder encoder;
 private final LoginRateLimiter limiter;
 private final RbacService rbac;
 public AuthController(AuthenticationManager manager, SecurityContextRepository contexts,PennyUserRepository users,PasswordEncoder encoder,LoginRateLimiter limiter,RbacService rbac){
  this.manager=manager;this.contexts=contexts;this.users=users;this.encoder=encoder;this.limiter=limiter;this.rbac=rbac;
 }
 public record Login(String email,String password){}
 public record Change(String currentPassword,String newPassword){}
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken());}
 @PostMapping("/login") public Map<String,Object> login(@RequestBody Login input,HttpServletRequest request,HttpServletResponse response){
  String key=limiter.key(request,input.email());
  limiter.check(key);
  Authentication authentication;
  try { authentication=manager.authenticate(new UsernamePasswordAuthenticationToken(input.email(),input.password())); }
  catch (AuthenticationException ex) { limiter.failure(key); throw ex; }
  limiter.success(key);
  request.getSession(true);request.changeSessionId();
  PennyUser loginUser=users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
  request.getSession().setAttribute("PENNY_SESSION_VERSION",loginUser.sessionVersion);
  request.getSession().setMaxInactiveInterval(30*24*60*60);
  request.getSession().setAttribute("PENNY_SESSION_EXPIRES_AT",Instant.now().plus(java.time.Duration.ofDays(30)).toEpochMilli());
  SecurityContext context=SecurityContextHolder.createEmptyContext();
  context.setAuthentication(authentication);
  SecurityContextHolder.setContext(context);
  contexts.saveContext(context,request,response);
  return profile(authentication.getName());
 }
 @GetMapping("/me") public Map<String,Object> me(Authentication auth){return profile(auth.getName());}
 private Map<String,Object> profile(String email){
  PennyUser u=users.findByEmailIgnoreCase(email).orElseThrow();
  return Map.ofEntries(
   Map.entry("email",u.email),Map.entry("name",u.fullName),Map.entry("mustChangePassword",u.mustChangePassword),
   Map.entry("canManageData",!u.mustChangePassword&&rbac.allowed(u.id,"DATA_MANAGE")),
   Map.entry("canManageUsers",!u.mustChangePassword&&rbac.allowed(u.id,"USERS_READ")),
   Map.entry("canReadTraining",!u.mustChangePassword&&rbac.allowed(u.id,"TRAINING_READ")),
   Map.entry("canWriteTraining",!u.mustChangePassword&&rbac.allowed(u.id,"TRAINING_WRITE")),
   Map.entry("canReadContracts",!u.mustChangePassword&&rbac.allowed(u.id,"CONTRACTS_READ")),
   Map.entry("canWriteContracts",!u.mustChangePassword&&rbac.allowed(u.id,"CONTRACTS_WRITE")),
   Map.entry("canReadActivities",!u.mustChangePassword&&rbac.allowed(u.id,"ACTIVITIES_READ")),
   Map.entry("canWriteActivities",!u.mustChangePassword&&rbac.allowed(u.id,"ACTIVITIES_WRITE")),
   Map.entry("canReadCustomers",!u.mustChangePassword&&rbac.allowed(u.id,"CUSTOMERS_READ")),Map.entry("canWriteCustomers",!u.mustChangePassword&&rbac.allowed(u.id,"CUSTOMERS_WRITE")),
   Map.entry("canReadProducts",!u.mustChangePassword&&rbac.allowed(u.id,"PRODUCTS_READ")),Map.entry("canWriteProducts",!u.mustChangePassword&&rbac.allowed(u.id,"PRODUCTS_WRITE")),
   Map.entry("canReadCatalog",!u.mustChangePassword&&rbac.allowed(u.id,"CATALOG_READ")),Map.entry("canWriteCatalog",!u.mustChangePassword&&rbac.allowed(u.id,"CATALOG_WRITE")),
   Map.entry("canReadSales",!u.mustChangePassword&&rbac.allowed(u.id,"SALES_READ")),Map.entry("canWriteSales",!u.mustChangePassword&&rbac.allowed(u.id,"SALES_WRITE")),
   Map.entry("canReadInventory",!u.mustChangePassword&&rbac.allowed(u.id,"INVENTORY_READ")),Map.entry("canReadAllocations",!u.mustChangePassword&&rbac.allowed(u.id,"ALLOCATIONS_READ")&&rbac.allowed(u.id,"INVENTORY_READ")&&rbac.allowed(u.id,"PRODUCTS_READ")),
   Map.entry("canWriteAllocations",!u.mustChangePassword&&rbac.allowed(u.id,"ALLOCATIONS_WRITE")&&rbac.allowed(u.id,"INVENTORY_READ")&&rbac.allowed(u.id,"PRODUCTS_READ")&&rbac.allowed(u.id,"PRODUCTS_WRITE")),
   Map.entry("canReadWorkstations",!u.mustChangePassword&&rbac.allowed(u.id,"WORKSTATIONS_READ")),Map.entry("canWriteWorkstations",!u.mustChangePassword&&rbac.allowed(u.id,"WORKSTATIONS_WRITE")),
   Map.entry("canUseTrash",!u.mustChangePassword&&rbac.allowed(u.id,"TRASH_READ")),Map.entry("canDeleteRecords",!u.mustChangePassword&&rbac.allowed(u.id,"TRASH_DELETE")),Map.entry("canRestoreTrash",!u.mustChangePassword&&rbac.allowed(u.id,"TRASH_RESTORE")));

 }
 @PostMapping("/change-password")
 public Map<String,Object> change(@RequestBody Change input,Authentication auth,HttpServletRequest request){
  PennyUser u=users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
  if(!encoder.matches(input.currentPassword(),u.passwordHash))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password attuale non corretta");
  if(input.newPassword()==null || input.newPassword().length()<12 || input.newPassword().length()>128
      || input.newPassword().equals(input.currentPassword()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nuova password non valida (12-128 caratteri, diversa dalla precedente)");
  u.passwordHash=encoder.encode(input.newPassword());u.mustChangePassword=false;u.passwordChangedAt=Instant.now();u.sessionVersion++;users.save(u);
  request.getSession().invalidate();SecurityContextHolder.clearContext();
  return Map.of("changed",true);
 }
 @ExceptionHandler(LoginRateLimiter.TooManyRequests.class)
 ResponseEntity<Map<String,String>> rateLimited(){return ResponseEntity.status(429).header("Retry-After","900").body(Map.of("error","Troppi tentativi di accesso. Riprova più tardi."));}
 @ExceptionHandler(AuthenticationException.class)
 ResponseEntity<Map<String,String>> authFailed(){return ResponseEntity.status(401).body(Map.of("error","Credenziali non valide"));}
 @ExceptionHandler(BadCredentialsException.class)
 ResponseEntity<Map<String,String>> invalid(){return ResponseEntity.status(401).body(Map.of("error","Credenziali non valide"));}
}
