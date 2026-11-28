package com.earogyata.core;
import java.time.*;
import jakarta.validation.constraints.*;
public final class ApiModels {
 private ApiModels() {}
 public record UserView(long id,String name,String email,String role) {}
 public record Credentials(@NotBlank @Email @Size(max=190) String email,@NotBlank @Size(max=72) String password) {}
 public record Register(@NotBlank @Size(max=80) String name,@NotBlank @Email @Size(max=190) String email,@NotBlank @Size(min=12,max=72) String password) {}
 public record CreateUser(@NotBlank @Size(max=80) String name,@NotBlank @Email @Size(max=190) String email,@NotBlank @Size(min=12,max=72) String password,@NotNull @Pattern(regexp="PATIENT|DOCTOR|ADMIN|WARD_STAFF") String role,@Size(max=80) String specialty,@Size(max=40) String room) {}
 public record AuthResponse(String accessToken,long expiresIn,UserView user) {}
 public record DoctorView(long id,long userId,String name,String specialty,String room) {}
 public record SessionView(long id,long doctorId,String doctorName,String specialty,String room,LocalDate date,int averageMinutes,String state,int waiting,int booked,Integer servingTicket) {}
 public record SlotView(long id,long sessionId,Instant startsAt,boolean available) {}
 public record CreateSession(@Positive long doctorId,@NotNull LocalDate date,@Min(1) @Max(120) int averageMinutes,@NotNull LocalTime startTime,@Min(1) @Max(60) int slotMinutes,@Min(1) @Max(80) int slotCount) {}
 public record BookAppointment(@Positive long slotId) {}
 public record AppointmentView(long id,long sessionId,long slotId,long patientId,String patientName,String doctorName,String specialty,LocalDate date,Instant startsAt,int ticketNumber,String status,Integer position,Integer estimatedWaitMinutes) {}
 public record WardView(long id,String name,String floor) {}
 public record BedView(long id,long wardId,String wardName,String floor,String label,String status) {}
 public record ReserveBed(@Positive long bedId,@Positive Long patientId) {}
 public record ReservationView(long id,long bedId,String bedLabel,String wardName,long patientId,String patientName,String status,Instant reservedAt) {}
 public record BedStatus(@NotNull @Pattern(regexp="AVAILABLE|CLEANING|OUT_OF_SERVICE") String status) {}
 public record AuditView(long id,Long actorId,String action,String resourceType,String resourceId,String outcome,Instant occurredAt) {}
}
