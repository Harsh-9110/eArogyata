package com.earogyata.queue;
import static com.earogyata.core.ApiModels.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import com.earogyata.auth.AppPrincipal;
import com.earogyata.core.*;
import com.earogyata.events.DomainChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class QueueService {
 private final JdbcTemplate db;private final AppSettings settings;private final Audit audit;private final ApplicationEventPublisher events;
 public QueueService(JdbcTemplate db,AppSettings settings,Audit audit,ApplicationEventPublisher events) {this.db=db;this.settings=settings;this.audit=audit;this.events=events;}
 private static final String SESSION_SQL="""
 SELECT s.*,d.specialty,d.room,u.name doctor_name,
 (SELECT COUNT(*) FROM queue_entries q WHERE q.session_id=s.id AND q.status='WAITING') waiting,
 (SELECT COUNT(*) FROM queue_entries q WHERE q.session_id=s.id AND q.status='BOOKED') booked,
 (SELECT MAX(ticket_number) FROM queue_entries q WHERE q.session_id=s.id AND q.status='IN_CONSULTATION') serving_ticket
 FROM opd_sessions s JOIN doctors d ON d.id=s.doctor_id JOIN users u ON u.id=d.user_id
 """;
 private static final RowMapper<SessionView> SESSION=(r,n)->new SessionView(r.getLong("id"),r.getLong("doctor_id"),r.getString("doctor_name"),r.getString("specialty"),r.getString("room"),r.getObject("session_date",LocalDate.class),r.getInt("average_minutes"),r.getString("state"),r.getInt("waiting"),r.getInt("booked"),r.getObject("serving_ticket")==null?null:r.getInt("serving_ticket"));
 private static final String APPOINTMENT_SQL="""
 SELECT q.*,p.name patient_name,du.name doctor_name,d.specialty,s.session_date,s.average_minutes,sl.starts_at,
 (SELECT COUNT(*) FROM queue_entries a WHERE a.session_id=q.session_id AND a.status='WAITING' AND a.ticket_number<q.ticket_number) ahead,
 (SELECT COUNT(*) FROM queue_entries a WHERE a.session_id=q.session_id AND a.status='IN_CONSULTATION') consulting
 FROM queue_entries q JOIN users p ON p.id=q.patient_id JOIN opd_sessions s ON s.id=q.session_id
 JOIN doctors d ON d.id=s.doctor_id JOIN users du ON du.id=d.user_id JOIN appointment_slots sl ON sl.id=q.slot_id
 """;
 private static final RowMapper<AppointmentView> APPOINTMENT=(r,n)->{
  String status=r.getString("status");Integer pos=null,wait=null;
  if(status.equals("WAITING")) {pos=r.getInt("ahead")+1;wait=WorkflowRules.estimatedWait(r.getInt("ahead"),r.getInt("consulting")>0,r.getInt("average_minutes"));}
  if(status.equals("IN_CONSULTATION")) {pos=0;wait=0;}
  return new AppointmentView(r.getLong("id"),r.getLong("session_id"),r.getLong("slot_id"),r.getLong("patient_id"),r.getString("patient_name"),r.getString("doctor_name"),r.getString("specialty"),r.getObject("session_date",LocalDate.class),r.getTimestamp("starts_at").toInstant(),r.getInt("ticket_number"),status,pos,wait);
 };
 public List<DoctorView> doctors() {return db.query("SELECT d.*,u.name FROM doctors d JOIN users u ON u.id=d.user_id WHERE u.enabled=TRUE ORDER BY u.name LIMIT 200",(r,n)->new DoctorView(r.getLong("id"),r.getLong("user_id"),r.getString("name"),r.getString("specialty"),r.getString("room")));}
 public List<SessionView> sessions(LocalDate date) {return db.query(SESSION_SQL+" WHERE s.session_date=? ORDER BY u.name,s.id LIMIT 200",SESSION,date==null?LocalDate.now(settings.zone):date);}
 private SessionView session(long id) {return db.query(SESSION_SQL+" WHERE s.id=?",SESSION,id).stream().findFirst().orElseThrow(ApiException::missing);}
 private Map<String,Object> lockSession(long id) {
  return db.queryForList("SELECT s.*,d.user_id doctor_user_id FROM opd_sessions s JOIN doctors d ON d.id=s.doctor_id WHERE s.id=? FOR UPDATE",id).stream().findFirst().orElseThrow(ApiException::missing);
 }
 private void requireDoctor(AppPrincipal p,Map<String,Object> session) {
  if(!p.is("ADMIN") && !(p.is("DOCTOR") && ((Number)session.get("doctor_user_id")).longValue()==p.id())) throw ApiException.forbidden();
 }
 public List<SlotView> slots(long id) {
  session(id);
  return db.query("SELECT sl.*,s.state,NOT EXISTS(SELECT 1 FROM queue_entries q WHERE q.active_slot_id=sl.id) unbooked FROM appointment_slots sl JOIN opd_sessions s ON s.id=sl.session_id WHERE sl.session_id=? ORDER BY starts_at LIMIT 80",
   (r,n)->new SlotView(r.getLong("id"),r.getLong("session_id"),r.getTimestamp("starts_at").toInstant(),r.getBoolean("unbooked") && r.getString("state").equals("OPEN") && r.getTimestamp("starts_at").toInstant().isAfter(Instant.now())),id);
 }
 @Transactional public SessionView createSession(AppPrincipal p,CreateSession b) {
  p.require("ADMIN");
  Instant first=b.date().atTime(b.startTime()).atZone(settings.zone).toInstant();
  Instant end=first.plusSeconds((long)b.slotMinutes()*60*b.slotCount());
  if(!first.isAfter(Instant.now()) || end.isAfter(b.date().plusDays(1).atStartOfDay(settings.zone).toInstant())) throw new ApiException(400,"Slots must be in the future and fit in one hospital calendar day");
  if(db.queryForObject("SELECT COUNT(*) FROM doctors d JOIN users u ON u.id=d.user_id WHERE d.id=? AND u.enabled=TRUE",Integer.class,b.doctorId())==0) throw ApiException.missing();
  long id=Sql.insert(db,"INSERT INTO opd_sessions(doctor_id,session_date,average_minutes) VALUES (?,?,?)",b.doctorId(),b.date(),b.averageMinutes());
  for(int i=0;i<b.slotCount();i++)db.update("INSERT INTO appointment_slots(session_id,starts_at) VALUES (?,?)",id,Timestamp.from(first.plusSeconds((long)i*b.slotMinutes()*60)));
  audit.add(p.id(),"SESSION_CREATED","session",id);events.publishEvent(new DomainChanged("queue"));return session(id);
 }
 private AppointmentView appointment(long id) {return db.query(APPOINTMENT_SQL+" WHERE q.id=?",APPOINTMENT,id).stream().findFirst().orElseThrow(ApiException::missing);}
 public List<AppointmentView> mine(AppPrincipal p) {p.require("PATIENT");return db.query(APPOINTMENT_SQL+" WHERE q.patient_id=? ORDER BY q.id DESC LIMIT 100",APPOINTMENT,p.id());}
 public List<AppointmentView> queue(AppPrincipal p,long sessionId) {
  var s=db.queryForList("SELECT d.user_id doctor_user_id FROM opd_sessions s JOIN doctors d ON d.id=s.doctor_id WHERE s.id=?",sessionId).stream().findFirst().orElseThrow(ApiException::missing);
  requireDoctor(p,s);audit.add(p.id(),"QUEUE_READ","session",sessionId);
  return db.query(APPOINTMENT_SQL+" WHERE q.session_id=? AND q.status IN ('WAITING','IN_CONSULTATION') ORDER BY q.ticket_number LIMIT 80",APPOINTMENT,sessionId);
 }
 @Transactional public AppointmentView book(AppPrincipal p,long slotId) {
  p.require("PATIENT");
  var slot=db.queryForList("SELECT * FROM appointment_slots WHERE id=?",slotId).stream().findFirst().orElseThrow(ApiException::missing);
  long sid=((Number)slot.get("session_id")).longValue();var s=lockSession(sid);
  db.queryForList("SELECT id FROM appointment_slots WHERE id=? FOR UPDATE",slotId);
  if(!"OPEN".equals(s.get("state")) || !((Timestamp)slot.get("starts_at")).toInstant().isAfter(Instant.now())) throw ApiException.conflict("This slot is no longer bookable");
  if(db.queryForObject("SELECT COUNT(*) FROM queue_entries WHERE active_slot_id=? OR (session_id=? AND active_patient_id=?)",Integer.class,slotId,sid,p.id())>0) throw ApiException.conflict("Slot unavailable or you already have an active appointment for this session");
  int ticket=((Number)s.get("next_ticket")).intValue();
  long id=Sql.insert(db,"INSERT INTO queue_entries(slot_id,session_id,patient_id,ticket_number) VALUES (?,?,?,?)",slotId,sid,p.id(),ticket);
  db.update("UPDATE opd_sessions SET next_ticket=next_ticket+1 WHERE id=?",sid);
  audit.add(p.id(),"APPOINTMENT_BOOKED","appointment",id);events.publishEvent(new DomainChanged("queue"));return appointment(id);
 }
 @Transactional public AppointmentView change(AppPrincipal p,long id,String action) {
  var initial=appointment(id);var s=lockSession(initial.sessionId());
  var q=db.queryForList("SELECT * FROM queue_entries WHERE id=? FOR UPDATE",id).get(0);
  String current=(String)q.get("status");long owner=((Number)q.get("patient_id")).longValue();String next;
  switch(action) {
   case "check-in" -> {
    if(p.id()!=owner && !p.is("ADMIN"))requireDoctor(p,s);
    if(!initial.date().equals(LocalDate.now(settings.zone)))throw ApiException.conflict("Check in on your appointment day");
    if(current.equals("WAITING"))return appointment(id);
    if(!current.equals("BOOKED"))throw ApiException.conflict("Only booked appointments can check in");next="WAITING";
    db.update("UPDATE queue_entries SET checked_in_at=CURRENT_TIMESTAMP WHERE id=?",id);
   }
   case "cancel" -> {
    if(p.id()!=owner && !p.is("ADMIN"))throw ApiException.forbidden();
    if(!WorkflowRules.canCancelAppointment(current))throw ApiException.conflict("This appointment cannot be cancelled");next="CANCELLED";
   }
   case "complete" -> {
    requireDoctor(p,s);if(!current.equals("IN_CONSULTATION"))throw ApiException.conflict("Only the current consultation can complete");next="COMPLETED";
    db.update("UPDATE queue_entries SET completed_at=CURRENT_TIMESTAMP WHERE id=?",id);
   }
   default -> throw new ApiException(400,"Unknown queue action");
  }
  db.update("UPDATE queue_entries SET status=? WHERE id=?",next,id);audit.add(p.id(),"APPOINTMENT_"+next,"appointment",id);events.publishEvent(new DomainChanged("queue"));return appointment(id);
 }
 @Transactional public AppointmentView next(AppPrincipal p,long sid) {
  var s=lockSession(sid);requireDoctor(p,s);
  if(!session(sid).date().equals(LocalDate.now(settings.zone)))throw ApiException.conflict("Queue is not for today");
  if(db.queryForObject("SELECT COUNT(*) FROM queue_entries WHERE serving_session_id=?",Integer.class,sid)>0)throw ApiException.conflict("Complete the current consultation first");
  var rows=db.queryForList("SELECT id FROM queue_entries WHERE session_id=? AND status='WAITING' ORDER BY ticket_number LIMIT 1 FOR UPDATE",sid);
  if(rows.isEmpty())throw ApiException.conflict("No checked-in patients are waiting");
  long id=((Number)rows.get(0).get("id")).longValue();db.update("UPDATE queue_entries SET status='IN_CONSULTATION' WHERE id=?",id);
  audit.add(p.id(),"PATIENT_CALLED","appointment",id);events.publishEvent(new DomainChanged("queue"));return appointment(id);
 }
}
