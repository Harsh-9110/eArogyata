package com.earogyata.events;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.earogyata.auth.AppPrincipal;
import com.earogyata.core.ApiException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@Component
public class EventHub {
 private record Client(AppPrincipal user,SseEmitter emitter) {}
 private final ConcurrentHashMap<UUID,Client> clients=new ConcurrentHashMap<>();
 private final JdbcTemplate db;
 public EventHub(JdbcTemplate db) { this.db=db; }
 public synchronized SseEmitter connect(AppPrincipal p) {
  if(clients.size()>=256 || clients.values().stream().filter(c->c.user.id()==p.id()).count()>=4) throw new ApiException(429,"Too many event streams");
  long lifetime=Math.max(1,Duration.between(Instant.now(),p.expiresAt()).toMillis());
  SseEmitter emitter=new SseEmitter(lifetime);UUID id=UUID.randomUUID();Client c=new Client(p,emitter);clients.put(id,c);
  emitter.onCompletion(()->clients.remove(id));emitter.onTimeout(()->{clients.remove(id);emitter.complete();});emitter.onError(e->clients.remove(id));
  send(id,c,"invalidate","queue");send(id,c,"invalidate","beds");return emitter;
 }
 private void send(UUID id,Client c,String event,String resource) {
  if(!c.user.expiresAt().isAfter(Instant.now())) {clients.remove(id);c.emitter.complete();return;}
  try {c.emitter.send(SseEmitter.event().name(event).data(Map.of("resource",resource),MediaType.APPLICATION_JSON));}
  catch(IOException|IllegalStateException e) {clients.remove(id);c.emitter.complete();}
 }
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) public void changed(DomainChanged event) {
  clients.forEach((id,c)->send(id,c,"invalidate",event.resource()));
 }
 @Scheduled(fixedDelay=15000) public void heartbeat() {
  clients.forEach((id,c)->{
   Integer valid=db.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE id=? AND revoked=FALSE AND expires_at>CURRENT_TIMESTAMP",Integer.class,c.user.sessionId());
   if(valid==null || valid==0) {clients.remove(id);c.emitter.complete();} else send(id,c,"heartbeat","heartbeat");
  });
 }
}
