package com.earogyata.auth;
import static com.earogyata.core.ApiModels.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.earogyata.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
@Service
public class AuthService {
 private final JdbcTemplate db; private final PasswordEncoder passwords; private final JwtEncoder jwt;
 private final AppSettings settings; private final Audit audit; private final RateLimiter limits; private final TransactionTemplate tx;
 private final SecureRandom random=new SecureRandom(); private final String dummyHash;
 public record Issued(AuthResponse response,String refreshToken) {}
 public AuthService(JdbcTemplate db,PasswordEncoder passwords,JwtEncoder jwt,AppSettings settings,Audit audit,RateLimiter limits,org.springframework.transaction.PlatformTransactionManager manager) {
  this.db=db;this.passwords=passwords;this.jwt=jwt;this.settings=settings;this.audit=audit;this.limits=limits;this.tx=new TransactionTemplate(manager);
  this.dummyHash=passwords.encode("Nonexistent-account-timing-padding");
 }
 public static String canonical(String email) { return email.trim().toLowerCase(Locale.ROOT); }
 private void validatePassword(String password) { if(password.getBytes(StandardCharsets.UTF_8).length>72) throw new ApiException(400,"Password must be at most 72 UTF-8 bytes"); }
 public UserView user(long id) {
  return db.query("SELECT id,name,email,role FROM users WHERE id=?",(r,n)->new UserView(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)),id).stream().findFirst().orElseThrow(ApiException::missing);
 }
 @Transactional public UserView create(String name,String email,String password,String role,String specialty,String room,Long actor) {
  validatePassword(password);
  if(name.trim().isEmpty()) throw new ApiException(400,"Name is required");
  if(role.equals("DOCTOR") && (specialty==null || specialty.isBlank() || room==null || room.isBlank())) throw new ApiException(400,"Doctors require specialty and room");
  KeyHolder key=new GeneratedKeyHolder();
  db.update(c->{PreparedStatement p=c.prepareStatement("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?)",Statement.RETURN_GENERATED_KEYS);p.setString(1,name.trim());p.setString(2,canonical(email));p.setString(3,passwords.encode(password));p.setString(4,role);return p;},key);
  long id=Objects.requireNonNull(key.getKey()).longValue();
  if(role.equals("DOCTOR")) db.update("INSERT INTO doctors(user_id,specialty,room) VALUES (?,?,?)",id,specialty.trim(),room.trim());
  audit.add(actor,"USER_CREATED","user",id); return user(id);
 }
 public Issued register(Register body) {
  limits.check("registration:"+canonical(body.email()),3);
  return tx.execute(s->{UserView u=create(body.name(),body.email(),body.password(),"PATIENT",null,null,null);return start(u);});
 }
 public Issued login(Credentials body) {
  validatePassword(body.password()); String email=canonical(body.email()); limits.check("login:"+email,8);
  var rows=db.queryForList("SELECT id,password_hash,enabled FROM users WHERE email=?",email);
  boolean match=passwords.matches(body.password(),rows.isEmpty()?dummyHash:(String)rows.get(0).get("password_hash"));
  if(rows.isEmpty() || !match || !Boolean.TRUE.equals(rows.get(0).get("enabled"))) {
   audit.add(null,"LOGIN_FAILED","account",RateLimiter.hash(email),"DENIED"); throw new ApiException(401,"Invalid email or password");
  }
  return tx.execute(s->start(user(((Number)rows.get(0).get("id")).longValue())));
 }
 private Issued start(UserView u) {
  String sid=UUID.randomUUID().toString(); Instant expires=Instant.now().plus(Duration.ofDays(settings.refreshDays));
  db.update("INSERT INTO auth_sessions(id,user_id,expires_at) VALUES (?,?,?)",sid,u.id(),Timestamp.from(expires));
  audit.add(u.id(),"LOGIN","session",sid); return issue(u,sid,expires);
 }
 private Issued issue(UserView u,String sid,Instant expires) {
  byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  db.update("INSERT INTO refresh_tokens(session_id,token_hash,expires_at) VALUES (?,?,?)",sid,RateLimiter.hash(token),Timestamp.from(expires));
  Instant now=Instant.now();
  JwtClaimsSet claims=JwtClaimsSet.builder().issuer(settings.issuer).audience(List.of(settings.audience)).subject(Long.toString(u.id())).issuedAt(now).expiresAt(now.plusSeconds(settings.accessSeconds)).claim("sid",sid).build();
  String access=jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
  return new Issued(new AuthResponse(access,settings.accessSeconds,u),token);
 }
 public Issued refresh(String token) {
  if(token==null || token.length()>256) throw new ApiException(401,"Refresh cookie required");
  Issued result=tx.execute(s->{
   var rows=db.queryForList("SELECT t.id,t.session_id,t.consumed,s.user_id,s.revoked,s.expires_at,u.enabled FROM refresh_tokens t JOIN auth_sessions s ON s.id=t.session_id JOIN users u ON u.id=s.user_id WHERE t.token_hash=? FOR UPDATE",RateLimiter.hash(token));
   if(rows.isEmpty()) return null;
   var row=rows.get(0);long uid=((Number)row.get("user_id")).longValue();String sid=(String)row.get("session_id");
   Instant expiry=((Timestamp)row.get("expires_at")).toInstant();
   if(Boolean.TRUE.equals(row.get("consumed"))) {
    db.update("UPDATE auth_sessions SET revoked=TRUE WHERE id=?",sid);audit.add(uid,"REFRESH_REPLAY","session",sid,"DENIED");return null;
   }
   if(Boolean.TRUE.equals(row.get("revoked")) || !Boolean.TRUE.equals(row.get("enabled")) || !expiry.isAfter(Instant.now())) return null;
   db.update("UPDATE refresh_tokens SET consumed=TRUE WHERE id=?",row.get("id"));
   audit.add(uid,"TOKEN_REFRESHED","session",sid);return issue(user(uid),sid,expiry);
  });
  // Throw outside the transaction: replay revocation must commit even though HTTP returns 401.
  if(result==null) throw new ApiException(401,"Invalid or expired refresh session");return result;
 }
 @Transactional public void logout(AppPrincipal p) { db.update("UPDATE auth_sessions SET revoked=TRUE WHERE id=? AND user_id=?",p.sessionId(),p.id());audit.add(p.id(),"LOGOUT","session",p.sessionId()); }
}
