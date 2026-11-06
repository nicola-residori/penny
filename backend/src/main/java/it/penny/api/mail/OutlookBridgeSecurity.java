package it.penny.api.mail;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class OutlookBridgeSecurity {
 // Narrow stateless boundary. Every handler validates a scoped bearer token itself;
 // cookie sessions cannot authorize these endpoints, and tokens authorize no other API.
 @Bean @Order(1) SecurityFilterChain outlookBridge(HttpSecurity http)throws Exception{
  return http.securityMatcher("/api/outlook/bridge/**").csrf(c->c.disable()).requestCache(c->c.disable())
   .sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(c->c.anyRequest().permitAll()).build();
 }
}
