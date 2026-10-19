package com.earogyata.core;
import java.sql.*;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.*;
public final class Sql {
 private Sql() {}
 public static long insert(JdbcTemplate db,String sql,Object...args) {
  KeyHolder keys=new GeneratedKeyHolder();
  db.update(c->{PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},keys);
  return Objects.requireNonNull(keys.getKey()).longValue();
 }
}
