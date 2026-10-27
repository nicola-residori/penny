package it.penny.api.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
public class PasswordGateFilter extends OncePerRequestFilter {
 private final PennyUserRepository users;
 public PasswordGateFilter(PennyUserRepository users){this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  Authentication a=SecurityContextHolder.getContext().getAuthentication();
  String path=request.getRequestURI();
  if(a!=null && a.isAuthenticated() && !"anonymousUser".equals(a.getPrincipal()) && path.startsWith("/api/")){
    PennyUser user=users.findByEmailIgnoreCase(a.getName()).orElse(null);
    if(user==null || !user.enabled){
      HttpSession session=request.getSession(false);
      if(session!=null)session.invalidate();
      SecurityContextHolder.clearContext();
      response.sendError(401,"Account non disponibile");return;
    }
    HttpSession currentSession=request.getSession(false);
    if(currentSession!=null){
      Object absolute=currentSession.getAttribute("PENNY_SESSION_EXPIRES_AT");
      long expires=absolute instanceof Long?(Long)absolute:currentSession.getCreationTime()+java.time.Duration.ofDays(30).toMillis();
      if(System.currentTimeMillis()>=expires){currentSession.invalidate();SecurityContextHolder.clearContext();response.sendError(401,"Sessione scaduta");return;}
    }
    Object version=currentSession==null?null:currentSession.getAttribute("PENNY_SESSION_VERSION");
    if(version==null || !(version instanceof Long) || ((Long)version).longValue()!=user.sessionVersion){
      if(currentSession!=null)currentSession.invalidate();
      SecurityContextHolder.clearContext();response.sendError(401,"Sessione revocata");return;
    }
    boolean allowed=path.equals("/api/auth/me") || path.equals("/api/auth/csrf")
       || path.equals("/api/auth/change-password") || path.equals("/api/auth/logout");
    if(user.mustChangePassword && !allowed){
      response.sendError(403,"Cambio password obbligatorio");return;
    }
  }
  chain.doFilter(request,response);
 }
}
