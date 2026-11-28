package com.earogyata.core;
import java.net.URI;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
@RestControllerAdvice
public class Errors {
 private final Audit audit;
 public Errors(Audit audit) { this.audit=audit; }
 private void recordDenied(int status) {
  var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  Long actor=authentication!=null && authentication.getPrincipal() instanceof com.earogyata.auth.AppPrincipal p ? p.id():null;
  audit.add(actor,"REQUEST_REJECTED","request",Integer.toString(status),"DENIED");
 }

 public static ProblemDetail problem(int status,String message) {
  ProblemDetail p=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),message);
  p.setType(URI.create("urn:earogyata:error:"+status)); return p;
 }
 @ExceptionHandler(ApiException.class) ResponseEntity<ProblemDetail> api(ApiException e) {
  if(e.status==403 || e.status==409 || e.status==429)recordDenied(e.status);
  var response=ResponseEntity.status(e.status);
  if(e.status==429)response.header("Retry-After","60");
  return response.body(problem(e.status,e.getMessage()));
 }
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,jakarta.validation.ConstraintViolationException.class})
 ResponseEntity<ProblemDetail> invalid(Exception e) { return ResponseEntity.badRequest().body(problem(400,"Request validation failed. Check field types, limits, and required fields.")); }
 @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ProblemDetail> denied(Exception e) { recordDenied(403); return ResponseEntity.status(403).body(problem(403,"This action is not allowed")); }
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ProblemDetail> conflict(Exception e) { recordDenied(409); return ResponseEntity.status(409).body(problem(409,"This operation conflicts with an existing booking or account")); }
 @ExceptionHandler({PessimisticLockingFailureException.class,org.springframework.dao.QueryTimeoutException.class}) ResponseEntity<ProblemDetail> busy(Exception e) { return ResponseEntity.status(409).body(problem(409,"Resource is busy. Refresh and try again.")); }
}
