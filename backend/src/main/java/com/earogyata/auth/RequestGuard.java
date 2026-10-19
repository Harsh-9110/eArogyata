package com.earogyata.auth;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.earogyata.core.*;
public class RequestGuard extends OncePerRequestFilter {
 private final AppSettings settings; private final RateLimiter limiter; private final ObjectMapper json; private final Audit audit;
 public RequestGuard(AppSettings settings,RateLimiter limiter,ObjectMapper json,Audit audit) { this.settings=settings; this.limiter=limiter; this.json=json; this.audit=audit; }
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  if(!req.getRequestURI().startsWith("/api/v1/") || req.getMethod().equals("OPTIONS")) { chain.doFilter(req,res); return; }
  var auth=SecurityContextHolder.getContext().getAuthentication();
  AppPrincipal p=auth!=null && auth.getPrincipal() instanceof AppPrincipal a ? a:null;
  try {
   String origin=req.getHeader("Origin");
   if(!java.util.Set.of("GET","HEAD","OPTIONS").contains(req.getMethod()) && origin!=null && !settings.origins.contains(origin)) throw ApiException.forbidden();
   if(req.getRequestURI().equals("/api/v1/auth/refresh") && (origin==null || !settings.origins.contains(origin))) throw ApiException.forbidden();
   if(req.getContentLengthLong()>16384) throw new ApiException(413,"Request body too large");
   limiter.check(p==null ? "peer:"+req.getRemoteAddr() : "user:"+p.id(),p==null?60:300);
  } catch(ApiException e) {
   audit.add(p==null?null:p.id(),e.status==429?"RATE_LIMITED":"REQUEST_REJECTED","request","redacted","DENIED");
   res.setStatus(e.status); res.setContentType("application/problem+json"); if(e.status==429) res.setHeader("Retry-After","60");
   json.writeValue(res.getOutputStream(),Errors.problem(e.status,e.getMessage())); return;
  }
  chain.doFilter(req,res);
 }
}
