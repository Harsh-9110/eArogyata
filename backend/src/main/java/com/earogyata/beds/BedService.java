package com.earogyata.beds;
import static com.earogyata.core.ApiModels.*;
import java.util.*;
import com.earogyata.auth.AppPrincipal;
import com.earogyata.core.*;
import com.earogyata.events.DomainChanged;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class BedService {
 private final JdbcTemplate db;private final Audit audit;private final ApplicationEventPublisher events;
 public BedService(JdbcTemplate db,Audit audit,ApplicationEventPublisher events) {this.db=db;this.audit=audit;this.events=events;}
 private static final String BED_SQL="""
 SELECT b.id,b.ward_id,w.name ward_name,w.floor,b.label,
 CASE WHEN r.status='ADMITTED' THEN 'OCCUPIED' WHEN r.status='RESERVED' THEN 'RESERVED' ELSE b.operational_status END visible_status
 FROM beds b JOIN wards w ON w.id=b.ward_id LEFT JOIN bed_reservations r ON r.active_bed_id=b.id
 """;
 private static final RowMapper<BedView> BED=(r,n)->new BedView(r.getLong("id"),r.getLong("ward_id"),r.getString("ward_name"),r.getString("floor"),r.getString("label"),r.getString("visible_status"));
 private static final String RESERVATION_SQL="""
 SELECT r.*,b.label,w.name ward_name,u.name patient_name FROM bed_reservations r JOIN beds b ON b.id=r.bed_id JOIN wards w ON w.id=b.ward_id JOIN users u ON u.id=r.patient_id
 """;
 private static final RowMapper<ReservationView> RESERVATION=(r,n)->new ReservationView(r.getLong("id"),r.getLong("bed_id"),r.getString("label"),r.getString("ward_name"),r.getLong("patient_id"),r.getString("patient_name"),r.getString("status"),r.getTimestamp("reserved_at").toInstant());
 public List<WardView> wards() {return db.query("SELECT * FROM wards ORDER BY id LIMIT 200",(r,n)->new WardView(r.getLong("id"),r.getString("name"),r.getString("floor")));}
 public List<BedView> beds() {return db.query(BED_SQL+" ORDER BY b.ward_id,b.label LIMIT 1000",BED);}
 private BedView bed(long id) {return db.query(BED_SQL+" WHERE b.id=?",BED,id).stream().findFirst().orElseThrow(ApiException::missing);}
 private ReservationView reservation(long id) {return db.query(RESERVATION_SQL+" WHERE r.id=?",RESERVATION,id).stream().findFirst().orElseThrow(ApiException::missing);}
 private Map<String,Object> lockBed(long id) {return db.queryForList("SELECT * FROM beds WHERE id=? FOR UPDATE",id).stream().findFirst().orElseThrow(ApiException::missing);}
 public List<ReservationView> reservations(AppPrincipal p) {
  p.require("PATIENT","ADMIN","WARD_STAFF");
  if(p.is("PATIENT"))return db.query(RESERVATION_SQL+" WHERE r.patient_id=? ORDER BY r.id DESC LIMIT 100",RESERVATION,p.id());
  audit.add(p.id(),"RESERVATIONS_READ","reservation","list");return db.query(RESERVATION_SQL+" ORDER BY r.id DESC LIMIT 100",RESERVATION);
 }
 @Transactional public ReservationView reserve(AppPrincipal p,ReserveBed request) {
  p.require("PATIENT","ADMIN","WARD_STAFF");
  long patient=request.patientId()==null?p.id():request.patientId();
  if(p.is("PATIENT") && patient!=p.id())throw ApiException.forbidden();
  if(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='PATIENT' AND enabled=TRUE",Integer.class,patient)==0)throw new ApiException(400,"Select an active patient account");
  var b=lockBed(request.bedId());
  if(!"AVAILABLE".equals(b.get("operational_status")))throw ApiException.conflict("Bed is not ready for reservation");
  if(db.queryForObject("SELECT COUNT(*) FROM bed_reservations WHERE active_bed_id=? OR active_patient_id=?",Integer.class,request.bedId(),patient)>0)throw ApiException.conflict("Bed unavailable or patient already has an active reservation");
  long id=Sql.insert(db,"INSERT INTO bed_reservations(bed_id,patient_id) VALUES (?,?)",request.bedId(),patient);
  audit.add(p.id(),"BED_RESERVED","reservation",id);events.publishEvent(new DomainChanged("beds"));return reservation(id);
 }
 @Transactional public ReservationView transition(AppPrincipal p,long id,String next) {
  var initial=reservation(id);
  if(next.equals("CANCELLED")) {
   if(!p.is("ADMIN","WARD_STAFF") && !(p.is("PATIENT") && p.id()==initial.patientId()))throw ApiException.forbidden();
  } else p.require("ADMIN","WARD_STAFF");
  lockBed(initial.bedId());
  var row=db.queryForList("SELECT * FROM bed_reservations WHERE id=? FOR UPDATE",id).get(0);
  if(!WorkflowRules.canTransitionReservation((String)row.get("status"),next))throw ApiException.conflict("Invalid reservation transition");
  db.update("UPDATE bed_reservations SET status=?,admitted_at=CASE WHEN ?='ADMITTED' THEN CURRENT_TIMESTAMP ELSE admitted_at END,ended_at=CASE WHEN ? IN ('DISCHARGED','CANCELLED') THEN CURRENT_TIMESTAMP ELSE ended_at END WHERE id=?",next,next,next,id);
  if(next.equals("DISCHARGED"))db.update("UPDATE beds SET operational_status='CLEANING' WHERE id=?",initial.bedId());
  audit.add(p.id(),"RESERVATION_"+next,"reservation",id);events.publishEvent(new DomainChanged("beds"));return reservation(id);
 }
 @Transactional public BedView status(AppPrincipal p,long id,String status) {
  p.require("ADMIN","WARD_STAFF");lockBed(id);
  if(db.queryForObject("SELECT COUNT(*) FROM bed_reservations WHERE active_bed_id=?",Integer.class,id)>0)throw ApiException.conflict("Cannot change operational status of a reserved or occupied bed");
  db.update("UPDATE beds SET operational_status=? WHERE id=?",status,id);
  audit.add(p.id(),"BED_"+status,"bed",id);events.publishEvent(new DomainChanged("beds"));return bed(id);
 }
}
