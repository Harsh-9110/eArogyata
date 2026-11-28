package com.earogyata.queue;
import static com.earogyata.core.ApiModels.*;
import java.time.LocalDate;
import java.util.List;
import com.earogyata.auth.AppPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class QueueController {
 private final QueueService service;
 public QueueController(QueueService service) {this.service=service;}
 @GetMapping("/doctors") public List<DoctorView> doctors() {return service.doctors();}
 @GetMapping("/sessions") public List<SessionView> sessions(@RequestParam(required=false) LocalDate date) {return service.sessions(date);}
 @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED) public SessionView create(@Valid @RequestBody CreateSession b,Authentication a) {return service.createSession(AppPrincipal.of(a),b);}
 @GetMapping("/sessions/{id}/slots") public List<SlotView> slots(@PathVariable long id) {return service.slots(id);}
 @GetMapping("/sessions/{id}/queue") public List<AppointmentView> queue(@PathVariable long id,Authentication a) {return service.queue(AppPrincipal.of(a),id);}
 @PostMapping("/sessions/{id}/next") public AppointmentView next(@PathVariable long id,Authentication a) {return service.next(AppPrincipal.of(a),id);}
 @GetMapping("/appointments") public List<AppointmentView> mine(Authentication a) {return service.mine(AppPrincipal.of(a));}
 @PostMapping("/appointments") @ResponseStatus(HttpStatus.CREATED) public AppointmentView book(@Valid @RequestBody BookAppointment b,Authentication a) {return service.book(AppPrincipal.of(a),b.slotId());}
 @PostMapping("/appointments/{id}/check-in") public AppointmentView checkIn(@PathVariable long id,Authentication a) {return service.change(AppPrincipal.of(a),id,"check-in");}
 @PostMapping("/appointments/{id}/cancel") public AppointmentView cancel(@PathVariable long id,Authentication a) {return service.change(AppPrincipal.of(a),id,"cancel");}
 @PostMapping("/appointments/{id}/complete") public AppointmentView complete(@PathVariable long id,Authentication a) {return service.change(AppPrincipal.of(a),id,"complete");}
}
