package com.earogyata.core;
import java.time.*;
import com.earogyata.auth.*;
import com.earogyata.core.ApiModels.*;
import com.earogyata.queue.QueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class Bootstrap implements ApplicationRunner {
 private final AuthService auth;private final JdbcTemplate db;private final QueueService queue;private final AppSettings settings;private final Environment env;
 private final String email,password,demoPassword;
 public Bootstrap(AuthService auth,JdbcTemplate db,QueueService queue,AppSettings settings,Environment env,@Value("${app.bootstrap-email}") String email,@Value("${app.bootstrap-password}") String password,@Value("${app.demo-password}") String demoPassword) {
  this.auth=auth;this.db=db;this.queue=queue;this.settings=settings;this.env=env;this.email=email;this.password=password;this.demoPassword=demoPassword;
 }
 private UserView ensure(String name,String email,String password,String role,String specialty,String room) {
  var ids=db.queryForList("SELECT id FROM users WHERE email=?",Long.class,AuthService.canonical(email));
  return ids.isEmpty()?auth.create(name,email,password,role,specialty,room,null):auth.user(ids.get(0));
 }
 @Override public void run(ApplicationArguments args) {
  if(!email.isBlank()) {
   if(password.length()<12)throw new IllegalStateException("Bootstrap password must have at least 12 characters");
   if(db.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'",Integer.class)==0)ensure("Hospital Administrator",email,password,"ADMIN",null,null);
  }
  if(!env.acceptsProfiles(org.springframework.core.env.Profiles.of("demo")))return;
  UserView admin=ensure("Demo Administrator","admin@demo.earogyata.test",demoPassword,"ADMIN",null,null);
  ensure("Demo Patient","patient@demo.earogyata.test",demoPassword,"PATIENT",null,null);
  ensure("Demo Ward Staff","ward@demo.earogyata.test",demoPassword,"WARD_STAFF",null,null);
  UserView doc=ensure("Demo Doctor","doctor@demo.earogyata.test",demoPassword,"DOCTOR","General Medicine","Room 101");
  ensure("Demo Cardiology Doctor","cardiology@demo.earogyata.test",demoPassword,"DOCTOR","Cardiology","Room 204");
  for(var row:db.queryForList("SELECT id FROM doctors")) {
   long doctorId=((Number)row.get("id")).longValue();
   for(int day=0;day<=2;day++) {
    LocalDate date=LocalDate.now(settings.zone).plusDays(day);
    if(db.queryForObject("SELECT COUNT(*) FROM opd_sessions WHERE doctor_id=? AND session_date=?",Integer.class,doctorId,date)>0)continue;
    LocalTime start=day==0?LocalTime.now(settings.zone).plusMinutes(5).withSecond(0).withNano(0):LocalTime.of(9,0);
    if(day==0 && (start.isBefore(LocalTime.now(settings.zone)) || start.isAfter(LocalTime.of(23,0))))continue;
    int count=Math.min(16,(int)Duration.between(start,LocalTime.of(23,59)).toMinutes()/12);
    if(count<1)continue;
    queue.createSession(new AppPrincipal(admin.id(),"ADMIN","bootstrap",Instant.now().plusSeconds(60)),new CreateSession(doctorId,date,12,start,12,count));
   }
  }
 }
}
