package com.earogyata.core;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class Audit {
 private final JdbcTemplate db;
 public Audit(JdbcTemplate db) { this.db=db; }
 public void add(Long actor,String action,String type,Object id) { add(actor,action,type,id,"SUCCESS"); }
 public void add(Long actor,String action,String type,Object id,String outcome) {
  db.update("INSERT INTO audit_events(actor_id,action,resource_type,resource_id,outcome) VALUES (?,?,?,?,?)",actor,action,type,String.valueOf(id),outcome);
 }
}
