package com.earogyata;

import com.earogyata.auth.AuthService;
import com.earogyata.core.ApiModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
abstract class IntegrationSupport {
 // One real database per JVM; Ryuk cleans it up. Tests never fall back to H2 or skip without Docker.
 static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
 static { MYSQL.start(); }
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
  p.add("spring.datasource.url",MYSQL::getJdbcUrl);
  p.add("spring.datasource.username",MYSQL::getUsername);
  p.add("spring.datasource.password",MYSQL::getPassword);
  p.add("app.jwt-secret",()->"test-only-32-byte-minimum-signing-key-42");
  p.add("app.cookie-secure",()->false);
  p.add("app.rate-limit-enabled",()->false);
 }
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired AuthService auth;
 @Autowired JdbcTemplate db;
 UserView patient,doctor,admin,ward;
 static final String PASSWORD="Integration-test42!";
 @BeforeEach void identities() {
  String prefix=UUID.randomUUID().toString();
  patient=auth.create("Test Patient",prefix+"p@example.test",PASSWORD,"PATIENT",null,null,null);
  doctor=auth.create("Test Doctor",prefix+"d@example.test",PASSWORD,"DOCTOR","General Medicine","Room T",null);
  admin=auth.create("Test Admin",prefix+"a@example.test",PASSWORD,"ADMIN",null,null,null);
  ward=auth.create("Test Ward",prefix+"w@example.test",PASSWORD,"WARD_STAFF",null,null,null);
 }
 String token(UserView u) { return auth.login(new Credentials(u.email(),PASSWORD)).response().accessToken(); }
 String body(Object o) throws Exception { return json.writeValueAsString(o); }
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,UserView u) {
  return get("/api/v1"+path).header("Authorization","Bearer "+token(u));
 }
 long doctorId() { return db.queryForObject("SELECT id FROM doctors WHERE user_id=?",Long.class,doctor.id()); }
}
