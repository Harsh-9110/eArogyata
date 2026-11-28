package com.earogyata;
import com.earogyata.core.WorkflowRules;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WorkflowRulesTest {
 @Test void illegalStateEdgesAndNegativeQueueInputsAreRejected() {
  assertThat(WorkflowRules.canTransitionReservation("ADMITTED","CANCELLED")).isFalse();
  assertThat(WorkflowRules.canTransitionReservation("DISCHARGED","ADMITTED")).isFalse();
  assertThat(WorkflowRules.canCancelAppointment("COMPLETED")).isFalse();
  assertThat(WorkflowRules.estimatedWait(3,true,12)).isEqualTo(48);
  assertThatThrownBy(()->WorkflowRules.estimatedWait(-1,false,12)).isInstanceOf(IllegalArgumentException.class);
 }
}
