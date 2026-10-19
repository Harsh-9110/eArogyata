package com.earogyata.auth;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.sql.Timestamp;
import java.util.HexFormat;
import com.earogyata.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class RateLimiter {
 private final JdbcTemplate db; private final AppSettings settings; private final TransactionTemplate tx;
 public RateLimiter(JdbcTemplate db,AppSettings settings,org.springframework.transaction.PlatformTransactionManager manager) {
  this.db=db; this.settings=settings; this.tx=new TransactionTemplate(manager);
  this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
 }
 public static String hash(String value) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
  catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
 }
 public void check(String identity,int maximum) {
  if(!settings.rateLimitEnabled) return;
  long window=Instant.now().getEpochSecond()/60;
  String key=hash(identity)+":"+window;
  Integer hits=tx.execute(status->{
   db.update("INSERT INTO rate_limit_buckets(bucket_key,hits,expires_at) VALUES (?,1,?) ON DUPLICATE KEY UPDATE hits=hits+1",key,Timestamp.from(Instant.ofEpochSecond((window+2)*60)));
   return db.queryForObject("SELECT hits FROM rate_limit_buckets WHERE bucket_key=?",Integer.class,key);
  });
  if(hits!=null && hits>maximum) throw new ApiException(429,"Too many requests. Retry after the current minute.");
 }
 @Scheduled(fixedDelay=60000) public void cleanup() { db.update("DELETE FROM rate_limit_buckets WHERE expires_at<CURRENT_TIMESTAMP LIMIT 5000"); }
}
