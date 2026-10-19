package com.earogyata.core;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class AppSettings {
 public final String issuer,audience;
 public final long accessSeconds,refreshDays;
 public final boolean cookieSecure,rateLimitEnabled;
 public final ZoneId zone;
 public final Set<String> origins;
 public AppSettings(@Value("${app.issuer}") String issuer,@Value("${app.audience}") String audience,
 @Value("${app.access-seconds}") long accessSeconds,@Value("${app.refresh-days}") long refreshDays,
 @Value("${app.cookie-secure}") boolean cookieSecure,@Value("${app.zone}") String zone,
 @Value("${app.allowed-origins}") String origins,@Value("${app.rate-limit-enabled}") boolean rateLimitEnabled) {
  this.issuer=issuer; this.audience=audience; this.accessSeconds=accessSeconds; this.refreshDays=refreshDays;
  this.cookieSecure=cookieSecure; this.zone=ZoneId.of(zone); this.rateLimitEnabled=rateLimitEnabled;
  this.origins=Set.copyOf(Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList());
  if(this.origins.isEmpty() || this.origins.contains("*")) throw new IllegalStateException("Exact allowed origins are required");
 }
}
