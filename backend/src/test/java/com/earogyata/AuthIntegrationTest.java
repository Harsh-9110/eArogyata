package com.earogyata;

import java.util.Map;
import com.earogyata.core.ApiModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationSupport {
 @org.springframework.beans.factory.annotation.Autowired org.springframework.security.oauth2.jwt.JwtEncoder signer;
 @Test void publicSignupCannotSelectStaffRole() throws Exception {
  mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
   .content(body(Map.of("name","Attacker","email","role@example.test","password",PASSWORD,"role","ADMIN"))))
   .andExpect(status().isBadRequest());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE email='role@example.test'",Integer.class)).isZero();
 }
 @Test void registrationHashesPasswordAndIssuesPatientOnly() throws Exception {
  String email=java.util.UUID.randomUUID()+"@example.test";
  mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
   .content(body(new Register("New Patient",email,PASSWORD))))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("PATIENT"))
   .andExpect(jsonPath("$.refreshToken").doesNotExist()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("HttpOnly")));
  String hash=db.queryForObject("SELECT password_hash FROM users WHERE email=?",String.class,email);
  assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
 }
 @Test void patientCannotProvisionStaffOrReadAudit() throws Exception {
  String t=token(patient);
  mvc.perform(get("/api/v1/admin/audit").header("Authorization","Bearer "+t)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/admin/users").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON)
   .content(body(new CreateUser("Escalation","esc@example.test",PASSWORD,"ADMIN",null,null)))).andExpect(status().isForbidden());
 }
 @Test void refreshRotatesAndReplayRevokesNewAccessToken() throws Exception {
  var session=auth.login(new Credentials(patient.email(),PASSWORD));
  var response=mvc.perform(post("/api/v1/auth/refresh").header("Origin","http://localhost:5173")
   .cookie(new Cookie("earogyata_refresh",session.refreshToken())))
   .andExpect(status().isOk()).andReturn().getResponse();
  String access=json.readTree(response.getContentAsString()).get("accessToken").asText();
  mvc.perform(post("/api/v1/auth/refresh").header("Origin","http://localhost:5173")
   .cookie(new Cookie("earogyata_refresh",session.refreshToken()))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='REFRESH_REPLAY' AND actor_id=?",Integer.class,patient.id())).isEqualTo(1);
 }
 @Test void forgedExpiredAndLoggedOutTokensAreRejected() throws Exception {
  String access=token(patient);
  String[] parts=access.split("\\.");
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+parts[0]+".e30."+parts[2])).andExpect(status().isUnauthorized());
  String sid=json.readTree(java.util.Base64.getUrlDecoder().decode(parts[1])).get("sid").asText();
  var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("e-arogyata").audience(java.util.List.of("e-arogyata-web")).subject(Long.toString(patient.id())).issuedAt(java.time.Instant.now().minusSeconds(2000)).expiresAt(java.time.Instant.now().minusSeconds(1000)).claim("sid",sid).build();
  String expired=signer.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+access)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/beds")).andExpect(status().isUnauthorized());
 }
 @Test void refreshRequiresAllowlistedOrigin() throws Exception {
  var session=auth.login(new Credentials(patient.email(),PASSWORD));
  mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("earogyata_refresh",session.refreshToken()))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/auth/refresh").header("Origin","https://evil.example").cookie(new Cookie("earogyata_refresh",session.refreshToken()))).andExpect(status().isForbidden());
 }
 @Test void inputLimitsAndInjectionCannotAuthenticate() throws Exception {
  mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
   .content(body(Map.of("email","' OR 1=1 --@example.test","password",PASSWORD)))).andExpect(status().is4xxClientError()).andExpect(jsonPath("$.accessToken").doesNotExist());
  mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
   .content(body(new Register("Too short","short@example.test","short")))).andExpect(status().isBadRequest());
 }
}
