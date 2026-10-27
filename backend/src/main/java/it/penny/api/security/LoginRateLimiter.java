package it.penny.api.security;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {
 private static final int MAX_ATTEMPTS=5;
 private static final long WINDOW_SECONDS=900;
 private final ConcurrentHashMap<String,Attempt> attempts=new ConcurrentHashMap<>();
 private record Attempt(int failures,long until){}
 public static class TooManyRequests extends RuntimeException{}
 public String key(HttpServletRequest req,String email){
   // Use the actual connection address, not spoofable forwarded headers.
   String normalized=email==null?"":email.trim().toLowerCase(Locale.ROOT);
   return req.getRemoteAddr()+"|"+normalized;
 }
 public void check(String key){
   Attempt a=attempts.get(key);
   if(a!=null && a.until()>Instant.now().getEpochSecond() && a.failures()>=MAX_ATTEMPTS)throw new TooManyRequests();
 }
 public void failure(String key){
   long now=Instant.now().getEpochSecond();
   attempts.compute(key,(k,old)->{
     if(old==null || old.until()<=now)return new Attempt(1,now+WINDOW_SECONDS);
     return new Attempt(old.failures()+1,old.until());
   });
   if(attempts.size()>10000)attempts.entrySet().removeIf(e->e.getValue().until()<=now);
 }
 public void success(String key){attempts.remove(key);}
}
