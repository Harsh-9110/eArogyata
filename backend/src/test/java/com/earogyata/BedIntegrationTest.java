package com.earogyata;
import com.earogyata.auth.AppPrincipal;
import com.earogyata.beds.BedService;
import com.earogyata.core.*;
import com.earogyata.core.ApiModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BedIntegrationTest extends IntegrationSupport {
 @Autowired BedService beds;
 AppPrincipal p(UserView u) {return new AppPrincipal(u.id(),u.role(),"test",Instant.now().plusSeconds(900));}
 long freshBed() {
  return Sql.insert(db,"INSERT INTO beds(ward_id,label) VALUES (1,?)","T-"+UUID.randomUUID().toString().substring(0,16));
 }
 @Test void twelveParallelPatientsCannotDoubleBookOneBed() throws Exception {
  long bed=freshBed();List<UserView> candidates=new ArrayList<>();
  for(int i=0;i<12;i++)candidates.add(auth.create("Race Patient "+i,"bed-race-"+UUID.randomUUID()+"@example.test",PASSWORD,"PATIENT",null,null,null));
  var result=ParallelRace.run(12,i->beds.reserve(p(candidates.get(i)),new ReserveBed(bed,null)));
  assertThat(result.successes()).isEqualTo(1);assertThat(result.conflicts()).isEqualTo(11);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM bed_reservations WHERE active_bed_id=?",Integer.class,bed)).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM bed_reservations WHERE bed_id=?",Integer.class,bed)).isEqualTo(1);
 }
 @Test void onePatientCannotHoldTwoDifferentBedsConcurrently() throws Exception {
  long[] ids={freshBed(),freshBed()};
  var result=ParallelRace.run(2,i->beds.reserve(p(patient),new ReserveBed(ids[i],null)));
  assertThat(result.successes()).isEqualTo(1);assertThat(result.conflicts()).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM bed_reservations WHERE active_patient_id=?",Integer.class,patient.id())).isEqualTo(1);
 }
 @Test void databaseConstraintStopsDuplicateActiveOccupancyEvenWithoutService() {
  long bed=freshBed();beds.reserve(p(patient),new ReserveBed(bed,null));
  assertThatThrownBy(()->db.update("INSERT INTO bed_reservations(bed_id,patient_id) VALUES (?,?)",bed,admin.id()))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void dischargeRequiresCleaningBeforeNextReservation() {
  long bed=freshBed();var r=beds.reserve(p(patient),new ReserveBed(bed,null));
  assertThatThrownBy(()->beds.transition(p(doctor),r.id(),"ADMITTED")).isInstanceOf(ApiException.class);
  assertThat(beds.transition(p(ward),r.id(),"ADMITTED").status()).isEqualTo("ADMITTED");
  assertThatThrownBy(()->beds.status(p(ward),bed,"AVAILABLE")).isInstanceOf(ApiException.class);
  beds.transition(p(ward),r.id(),"DISCHARGED");
  assertThat(beds.beds().stream().filter(b->b.id()==bed).findFirst().orElseThrow().status()).isEqualTo("CLEANING");
  assertThatThrownBy(()->beds.reserve(p(patient),new ReserveBed(bed,null))).isInstanceOf(ApiException.class);
  beds.status(p(ward),bed,"AVAILABLE");assertThat(beds.reserve(p(patient),new ReserveBed(bed,null)).status()).isEqualTo("RESERVED");
 }
 @Test void patientCannotReserveForOthersOrCancelTheirReservation() throws Exception {
  long bed=freshBed();var r=beds.reserve(p(patient),new ReserveBed(bed,null));
  var other=auth.create("Other Patient","bed-idor-"+UUID.randomUUID()+"@example.test",PASSWORD,"PATIENT",null,null,null);
  assertThatThrownBy(()->beds.reserve(p(other),new ReserveBed(freshBed(),patient.id()))).isInstanceOf(ApiException.class);
  mvc.perform(post("/api/v1/reservations/"+r.id()+"/cancel").header("Authorization","Bearer "+token(other))).andExpect(status().isForbidden());
  assertThat(beds.reservations(p(other))).isEmpty();
  mvc.perform(authenticatedGet("/beds",other)).andExpect(status().isOk()).andExpect(jsonPath("$[0].patientName").doesNotExist());
 }
 @Test void cancellationReleasesBedAndAuditRecordsSensitiveActions() {
  long bed=freshBed();var first=beds.reserve(p(patient),new ReserveBed(bed,null));beds.transition(p(patient),first.id(),"CANCELLED");
  var second=beds.reserve(p(patient),new ReserveBed(bed,null));assertThat(second.id()).isNotEqualTo(first.id());
  assertThat(db.queryForObject("SELECT COUNT(*) FROM audit_events WHERE actor_id=? AND action='BED_RESERVED'",Integer.class,patient.id())).isEqualTo(2);
 }
}
