package com.earogyata.beds;
import static com.earogyata.core.ApiModels.*;
import java.util.List;
import com.earogyata.auth.AppPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class BedController {
 private final BedService service;
 public BedController(BedService service) {this.service=service;}
 @GetMapping("/wards") public List<WardView> wards() {return service.wards();}
 @GetMapping("/beds") public List<BedView> beds() {return service.beds();}
 @PatchMapping("/beds/{id}/status") public BedView status(@PathVariable long id,@Valid @RequestBody BedStatus b,Authentication a) {return service.status(AppPrincipal.of(a),id,b.status());}
 @GetMapping("/reservations") public List<ReservationView> reservations(Authentication a) {return service.reservations(AppPrincipal.of(a));}
 @PostMapping("/reservations") @ResponseStatus(HttpStatus.CREATED) public ReservationView reserve(@Valid @RequestBody ReserveBed b,Authentication a) {return service.reserve(AppPrincipal.of(a),b);}
 @PostMapping("/reservations/{id}/admit") public ReservationView admit(@PathVariable long id,Authentication a) {return service.transition(AppPrincipal.of(a),id,"ADMITTED");}
 @PostMapping("/reservations/{id}/cancel") public ReservationView cancel(@PathVariable long id,Authentication a) {return service.transition(AppPrincipal.of(a),id,"CANCELLED");}
 @PostMapping("/reservations/{id}/discharge") public ReservationView discharge(@PathVariable long id,Authentication a) {return service.transition(AppPrincipal.of(a),id,"DISCHARGED");}
}
