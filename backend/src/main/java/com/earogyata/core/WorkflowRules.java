package com.earogyata.core;
import java.util.Set;
public final class WorkflowRules {
 private WorkflowRules() {}
 public static boolean canCancelAppointment(String status) { return Set.of("BOOKED","WAITING").contains(status); }
 public static boolean canTransitionReservation(String from,String to) {
  return (from.equals("RESERVED") && Set.of("ADMITTED","CANCELLED").contains(to)) || (from.equals("ADMITTED") && to.equals("DISCHARGED"));
 }
 public static int estimatedWait(int waitingAhead,boolean consulting,int averageMinutes) {
  if (waitingAhead<0 || averageMinutes<1) throw new IllegalArgumentException("Invalid queue estimate input");
  return Math.multiplyExact(waitingAhead+(consulting?1:0),averageMinutes);
 }
}
