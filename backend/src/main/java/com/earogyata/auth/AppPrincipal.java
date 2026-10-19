package com.earogyata.auth;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import com.earogyata.core.ApiException;
public record AppPrincipal(long id,String role,String sessionId,Instant expiresAt) {
 public boolean is(String... roles) { return java.util.Arrays.asList(roles).contains(role); }
 public void require(String... roles) { if(!is(roles)) throw ApiException.forbidden(); }
 public static AppPrincipal of(Authentication auth) {
  if(auth==null || !(auth.getPrincipal() instanceof AppPrincipal p)) throw new ApiException(401,"Authentication required");
  return p;
 }
}
