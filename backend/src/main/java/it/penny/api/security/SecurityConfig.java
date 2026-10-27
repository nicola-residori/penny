package it.penny.api.security;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean UserDetailsService userDetailsService(PennyUserRepository users){
  return username -> {
   PennyUser u=users.findByEmailIgnoreCase(username).orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
   return User.withUsername(u.email).password(u.passwordHash).disabled(!u.enabled).roles("USER").build();
  };
 }
 @Bean AuthenticationManager authenticationManager(UserDetailsService service, PasswordEncoder encoder){
  DaoAuthenticationProvider provider=new DaoAuthenticationProvider(service);
  provider.setPasswordEncoder(encoder);
  return new ProviderManager(provider);
 }
 @Bean SecurityContextRepository securityContextRepository(){return new HttpSessionSecurityContextRepository();}
 @Bean SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository repository,PennyUserRepository users) throws Exception {
  CookieCsrfTokenRepository csrf=CookieCsrfTokenRepository.withHttpOnlyFalse();
  csrf.setCookiePath("/");
  http.csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
    .securityContext(c->c.securityContextRepository(repository))
    .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
      .sessionFixation(f->f.changeSessionId()))
    .authorizeHttpRequests(a->a
      // Permit only container error dispatch, not an anonymous REQUEST to /error.
      .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
      .requestMatchers("/api/auth/csrf","/api/auth/login","/api/health","/actuator/health").permitAll()
      .requestMatchers("/api/auth/**").authenticated()
      .anyRequest().authenticated())
    .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{
      if ("POST".equals(req.getMethod()) && "/api/auth/login".equals(req.getServletPath())) {
        res.sendError(403);
      } else {
        res.sendError(401);
      }
    }))
    .formLogin(f->f.disable()).httpBasic(b->b.disable())
    .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)));
  http.addFilterAfter(new PasswordGateFilter(users),org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
  return http.build();
 }
}
