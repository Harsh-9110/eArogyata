import com.earogyata.core.WorkflowRules;
public final class WorkflowSmoke {
 public static void main(String[] args) {
  if(!WorkflowRules.canTransitionReservation("RESERVED","ADMITTED"))throw new AssertionError("Admission blocked");
  if(!WorkflowRules.canTransitionReservation("RESERVED","CANCELLED"))throw new AssertionError("Cancellation blocked");
  if(!WorkflowRules.canTransitionReservation("ADMITTED","DISCHARGED"))throw new AssertionError("Discharge blocked");
  if(WorkflowRules.canTransitionReservation("ADMITTED","CANCELLED"))throw new AssertionError("Admitted cancellation allowed");
  if(WorkflowRules.canTransitionReservation("DISCHARGED","ADMITTED"))throw new AssertionError("History reactivated");
  if(WorkflowRules.canCancelAppointment("IN_CONSULTATION"))throw new AssertionError("Active consultation cancellation allowed");
  if(WorkflowRules.estimatedWait(2,true,12)!=36)throw new AssertionError("Wait estimate wrong");
  if(WorkflowRules.estimatedWait(0,false,12)!=0)throw new AssertionError("Idle queue estimate wrong");
  boolean invalid=false;try{WorkflowRules.estimatedWait(-1,false,12);}catch(IllegalArgumentException e){invalid=true;}
  if(!invalid)throw new AssertionError("Negative queue position accepted");
  System.out.println("9 workflow smoke assertions passed. No database or Spring integration was exercised.");
 }
}
