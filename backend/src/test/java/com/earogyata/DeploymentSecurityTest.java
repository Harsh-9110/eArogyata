package com.earogyata;

import com.earogyata.auth.RateLimiter;
import com.earogyata.auth.SecurityConfig;
import com.earogyata.core.AppSettings;
import com.earogyata.core.Audit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Exercises the real security chain without starting a database or a container. */
@SpringJUnitWebConfig(DeploymentSecurityTest.TestConfig.class)
@TestPropertySource(properties="app.jwt-secret=deployment-test-only-signing-key-at-least-32-bytes")
class DeploymentSecurityTest {
 @Autowired WebApplicationContext context;
 MockMvc mvc;

 @BeforeEach void setup() { mvc=webAppContextSetup(context).apply(springSecurity()).build(); }

 @Test void browserCanLoadPublicUiAndSecurityPolicyAllowsLocalAssets() throws Exception {
  for(String path:new String[]{"/","/index.html","/assets/app.js","/favicon.svg"}) {
   mvc.perform(get(path)).andExpect(status().isOk())
    .andExpect(header().string("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
    .andExpect(header().string("X-Content-Type-Options","nosniff"));
  }
 }

 @Test void publishingUiDoesNotExposeApiOrPermitWritingToAssetPaths() throws Exception {
  mvc.perform(get("/api/v1/beds")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/admin/audit")).andExpect(status().isUnauthorized());
  mvc.perform(post("/assets/app.js")).andExpect(status().isUnauthorized());
 }

 @Configuration
 @EnableWebMvc
 @EnableWebSecurity
 @Import(SecurityConfig.class)
 static class TestConfig {
  @Bean JdbcTemplate jdbcTemplate() { return mock(JdbcTemplate.class); }
  @Bean RateLimiter rateLimiter() { return mock(RateLimiter.class); }
  @Bean Audit audit() { return mock(Audit.class); }
  @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
  @Bean AppSettings appSettings() { return new AppSettings("e-arogyata","e-arogyata-web",900,7,true,"Asia/Kolkata","https://hospital.example",true); }
  @Bean ProbeController probeController() { return new ProbeController(); }
 }

 @RestController
 static class ProbeController {
  @GetMapping({"/","/index.html","/assets/app.js","/favicon.svg"}) String ui() { return "asset"; }
  @GetMapping({"/api/v1/beds","/api/v1/admin/audit"}) String protectedApi() { return "private"; }
 }
}
