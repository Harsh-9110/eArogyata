package com.earogyata.auth;
import static com.earogyata.core.ApiModels.*;
import java.util.List;
import com.earogyata.core.Audit;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
 private final AuthService service;private final JdbcTemplate db;private final Audit audit;
 public AdminController(AuthService service,JdbcTemplate db,Audit audit) { this.service=service;this.db=db;this.audit=audit; }
 @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) public UserView create(@Valid @RequestBody CreateUser b,Authentication auth) {return service.create(b.name(),b.email(),b.password(),b.role(),b.specialty(),b.room(),AppPrincipal.of(auth).id());}
 @GetMapping("/users") public List<UserView> users(Authentication auth) {
  audit.add(AppPrincipal.of(auth).id(),"USERS_READ","user","list");
  return db.query("SELECT id,name,email,role FROM users ORDER BY id DESC LIMIT 200",(r,n)->new UserView(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)));
 }
 @GetMapping("/audit") public List<AuditView> events(@RequestParam(defaultValue="9223372036854775807") long before,Authentication auth) {
  audit.add(AppPrincipal.of(auth).id(),"AUDIT_READ","audit","list");
  return db.query("SELECT * FROM audit_events WHERE id<? ORDER BY id DESC LIMIT 100",(r,n)->new AuditView(r.getLong("id"),(Long)r.getObject("actor_id"),r.getString("action"),r.getString("resource_type"),r.getString("resource_id"),r.getString("outcome"),r.getTimestamp("occurred_at").toInstant()),before);
 }
}
