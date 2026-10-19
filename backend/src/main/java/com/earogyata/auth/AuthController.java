package com.earogyata.auth;
import static com.earogyata.core.ApiModels.*;
import com.earogyata.core.AppSettings;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthService service;private final AppSettings settings;
 public AuthController(AuthService service,AppSettings settings) { this.service=service;this.settings=settings; }
 private AuthResponse respond(AuthService.Issued issued,HttpServletResponse res) {
  res.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("earogyata_refresh",issued.refreshToken()).httpOnly(true).secure(settings.cookieSecure).sameSite("Strict").path("/api/v1/auth").maxAge(Duration.ofDays(settings.refreshDays)).build().toString());
  res.setHeader("Cache-Control","no-store");return issued.response();
 }
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) public AuthResponse register(@Valid @RequestBody Register body,HttpServletResponse res) { return respond(service.register(body),res); }
 @PostMapping("/login") public AuthResponse login(@Valid @RequestBody Credentials body,HttpServletResponse res) { return respond(service.login(body),res); }
 @PostMapping("/refresh") public AuthResponse refresh(@CookieValue(name="earogyata_refresh",required=false) String token,HttpServletResponse res) { return respond(service.refresh(token),res); }
 @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(Authentication auth,HttpServletResponse res) {
  service.logout(AppPrincipal.of(auth));res.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("earogyata_refresh","").httpOnly(true).secure(settings.cookieSecure).sameSite("Strict").path("/api/v1/auth").maxAge(0).build().toString());
 }
 @GetMapping("/me") public UserView me(Authentication auth) { return service.user(AppPrincipal.of(auth).id()); }
}
