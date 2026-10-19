package com.earogyata;
import com.earogyata.queue.QueueService;
import com.earogyata.auth.AppPrincipal;
import com.earogyata.core.*;
import com.earogyata.core.ApiModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.sql.Timestamp;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QueueIntegrationTest extends IntegrationSupport {
 @Autowired QueueService queue;
 @Autowired AppSettings settings;
 AppPrincipal p(UserView u) {return new AppPrincipal(u.id(),u.role(),"test",Instant.now().plusSeconds(900));}
 long seedSession() {
  long id=Sql.insert(db,"INSERT INTO opd_sessions(doctor_id,session_date,average_minutes) VALUES (?,?,12)",doctorId(),LocalDate.now(settings.zone));
  for(int i=1;i<=3;i++)db.update("INSERT INTO appointment_slots(session_id,starts_at) VALUES (?,?)",id,Timestamp.from(Instant.now().plusSeconds(600L*i)));
  return id;
 }
 long slot(long sid,int offset) {return db.queryForList("SELECT id FROM appointment_slots WHERE session_id=? ORDER BY id",Long.class,sid).get(offset);}
 @Test void concurrentSlotBookingHasExactlyOneWinner() throws Exception {
  long sid=seedSession(),slot=slot(sid,0);
  UserView second=auth.create("Second","slot-"+java.util.UUID.randomUUID()+"@example.test",PASSWORD,"PATIENT",null,null,null);
  var result=ParallelRace.run(2,i->queue.book(p(i==0?patient:second),slot));
  assertThat(result.successes()).isEqualTo(1);assertThat(result.conflicts()).isEqualTo(1);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM queue_entries WHERE active_slot_id=?",Integer.class,slot)).isEqualTo(1);
 }
 @Test void checkInCallCompleteAndCancellationFollowWorkflow() {
  long sid=seedSession();var booked=queue.book(p(patient),slot(sid,0));
  assertThat(booked.status()).isEqualTo("BOOKED");
  assertThatThrownBy(()->queue.next(p(doctor),sid)).isInstanceOf(ApiException.class);
  var waiting=queue.change(p(patient),booked.id(),"check-in");assertThat(waiting.position()).isEqualTo(1);
  assertThat(waiting.estimatedWaitMinutes()).isZero();
  assertThat(queue.next(p(doctor),sid).status()).isEqualTo("IN_CONSULTATION");
  assertThatThrownBy(()->queue.next(p(doctor),sid)).isInstanceOf(ApiException.class);
  assertThat(queue.change(p(doctor),booked.id(),"complete").status()).isEqualTo("COMPLETED");
  assertThatThrownBy(()->queue.change(p(patient),booked.id(),"cancel")).isInstanceOf(ApiException.class);
  var second=queue.book(p(patient),slot(sid,1));queue.change(p(patient),second.id(),"cancel");
  assertThat(queue.slots(sid).get(1).available()).isTrue();
 }
 @Test void patientCannotReadQueueOrModifySomeoneElsesAppointment() throws Exception {
  long sid=seedSession();var booked=queue.book(p(patient),slot(sid,0));
  UserView other=auth.create("Other","idor-"+java.util.UUID.randomUUID()+"@example.test",PASSWORD,"PATIENT",null,null,null);
  mvc.perform(request("/sessions/"+sid+"/queue",other)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/appointments/"+booked.id()+"/cancel").header("Authorization","Bearer "+token(other))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/sessions/"+sid+"/next").header("Authorization","Bearer "+token(ward))).andExpect(status().isForbidden());
  assertThat(queue.mine(p(other))).isEmpty();
 }
 @Test void unassignedDoctorCannotOperateAnotherDoctorsQueue() throws Exception {
  long sid=seedSession();var another=auth.create("Other Doctor","doctor-"+java.util.UUID.randomUUID()+"@example.test",PASSWORD,"DOCTOR","General","Other room",null);
  mvc.perform(request("/sessions/"+sid+"/queue",another)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/sessions/"+sid+"/next").header("Authorization","Bearer "+token(another))).andExpect(status().isForbidden());
 }
 @Test void ticketsAreMonotonicAndNextUsesWaitingOrder() {
  long sid=seedSession();UserView second=auth.create("Second","fifo-"+java.util.UUID.randomUUID()+"@example.test",PASSWORD,"PATIENT",null,null,null);
  var first=queue.book(p(patient),slot(sid,0));var last=queue.book(p(second),slot(sid,1));
  queue.change(p(second),last.id(),"check-in");queue.change(p(patient),first.id(),"check-in");
  assertThat(queue.next(p(doctor),sid).id()).isEqualTo(first.id());
  assertThat(queue.mine(p(second)).get(0).estimatedWaitMinutes()).isEqualTo(12);
  queue.change(p(doctor),first.id(),"complete");assertThat(queue.next(p(doctor),sid).id()).isEqualTo(last.id());
 }
 @Test void sessionValidationAndDuplicatePatientBookingAreEnforced() throws Exception {
  long sid=seedSession();queue.book(p(patient),slot(sid,0));
  mvc.perform(post("/api/v1/appointments").header("Authorization","Bearer "+token(patient)).contentType(MediaType.APPLICATION_JSON)
   .content(body(new BookAppointment(slot(sid,1))))).andExpect(status().isConflict());
  mvc.perform(post("/api/v1/sessions").header("Authorization","Bearer "+token(admin)).contentType(MediaType.APPLICATION_JSON)
   .content(body(new CreateSession(doctorId(),LocalDate.now(settings.zone).plusDays(1),12,LocalTime.of(23,50),30,3)))).andExpect(status().isBadRequest());
 }
 @Test void sseRequiresAuthenticationAndSendsResourceInvalidations() throws Exception {
  mvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
  var result=mvc.perform(request("/events",patient)).andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
  assertThat(result.getResponse().getContentAsString()).contains("event:invalidate","queue","beds").doesNotContain(patient.name(),patient.email());
  result.getRequest().getAsyncContext().complete();
 }
}
