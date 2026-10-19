package com.earogyata.auth;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.earogyata.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
 private SecretKeySpec key(String secret) {
  byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
  if(bytes.length<32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes of random secret material");
  return new SecretKeySpec(bytes,"HmacSHA256");
 }
 @Bean JwtEncoder encoder(@Value("${app.jwt-secret}") String secret) { return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret))); }
 @Bean JwtDecoder decoder(@Value("${app.jwt-secret}") String secret,AppSettings settings) {
  NimbusJwtDecoder d= NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
  OAuth2TokenValidator<Jwt> audience=jwt->jwt.getAudience()!=null && jwt.getAudience().contains(settings.audience)?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid audience",null));
  d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(settings.issuer),audience)); return d;
 }
 @Bean SecurityFilterChain security(HttpSecurity http,JdbcTemplate db,AppSettings settings,RateLimiter limiter,ObjectMapper json,Audit audit) throws Exception {
  http.csrf(c->c.disable()).cors(c->c.configurationSource(cors(settings)))
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .requestCache(c->c.disable())
   .authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC,jakarta.servlet.DispatcherType.ERROR).permitAll()
    .requestMatchers("/actuator/health","/openapi.json","/api/v1/auth/login","/api/v1/auth/register","/api/v1/auth/refresh").permitAll()
    .anyRequest().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/problem+json");json.writeValue(res.getOutputStream(),Errors.problem(401,"Authentication required"));})
    .accessDeniedHandler((req,res,ex)->{audit.add(null,"ACCESS_DENIED","request","redacted","DENIED");res.setStatus(403);res.setContentType("application/problem+json");json.writeValue(res.getOutputStream(),Errors.problem(403,"This action is not allowed"));}))
   .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(jwt->{
    try {
     long id=Long.parseLong(jwt.getSubject()); String sid=jwt.getClaimAsString("sid");
     if(sid==null || jwt.getExpiresAt()==null || !jwt.getExpiresAt().isAfter(Instant.now())) throw new InvalidBearerTokenException("Expired session");
     var rows=db.queryForList("SELECT u.role FROM users u JOIN auth_sessions s ON s.user_id=u.id WHERE u.id=? AND u.enabled=TRUE AND s.id=? AND s.revoked=FALSE AND s.expires_at>CURRENT_TIMESTAMP",id,sid);
     if(rows.isEmpty()) throw new InvalidBearerTokenException("Revoked session");
     String role=(String)rows.get(0).get("role");
     return new UsernamePasswordAuthenticationToken(new AppPrincipal(id,role,sid,jwt.getExpiresAt()),null,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
    } catch(NumberFormatException e) { throw new InvalidBearerTokenException("Invalid subject"); }
   })).authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/problem+json");json.writeValue(res.getOutputStream(),Errors.problem(401,"Invalid or expired access token"));}));
  http.addFilterAfter(new RequestGuard(settings,limiter,json,audit),BearerTokenAuthenticationFilter.class);
  http.headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
    .referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
    .cacheControl(org.springframework.security.config.Customizer.withDefaults()));
  return http.build();
 }
 private CorsConfigurationSource cors(AppSettings settings) {
  CorsConfiguration c=new CorsConfiguration(); c.setAllowedOrigins(new ArrayList<>(settings.origins)); c.setAllowedMethods(List.of("GET","POST","PATCH","OPTIONS"));
  c.setAllowedHeaders(List.of("Authorization","Content-Type")); c.setAllowCredentials(true); c.setMaxAge(3600L);
  UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",c); return source;
 }
}
