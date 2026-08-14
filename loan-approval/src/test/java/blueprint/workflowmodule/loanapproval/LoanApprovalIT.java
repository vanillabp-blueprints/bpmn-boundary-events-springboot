package blueprint.workflowmodule.loanapproval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import blueprint.workflowmodule.WorkflowModuleTest;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;

/**
 * The integration test of this workflow module: it starts a real workflow in a real BPMS
 * and lets the timers of the model decide what happens.
 *
 * <p>
 * Both tests wait for the aggregate rather than for a clock. A timer is the BPMS' business
 * and it fires when it fires; what the test asserts is what the workflow did about it.
 * </p>
 */
public class LoanApprovalIT extends WorkflowModuleTest {

  /** The surrounding system, replaced by a simulator the test can look at. */
  @TestConfiguration
  static class Simulators {

    @Bean
    @Primary
    PartnerApprovalSimulator partner() {

      return new PartnerApprovalSimulator();

    }

  }

  @Autowired
  private Service service;

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private PartnerApprovalSimulator partner;

  @BeforeEach
  public void forgetWhatThePreviousTestDid() {

    partner.reset();

  }

  private String startAndAwaitPartnerRequest(
      final String loanRequestId) {

    service.initiateLoanApproval(loanRequestId, 5000);

    // the timer in the sequence flow delays this: the request goes out AFTER the
    // cool-off period, not when the workflow started
    return awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getPartnerApprovalTaskId() != null)
        .getPartnerApprovalTaskId();

  }

  @Test
  @DisplayName("The non-interrupting event fires and the task stays open")
  public void aReminderLeavesTheTaskOpen() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var taskId = startAndAwaitPartnerRequest(loanRequestId);

    // A reminder ran. How many more follow is the engine's business - the cycle asks for
    // two, an embedded engine got one out before the deadline in our runs - so the test
    // waits for the first and answers while another one may still be running. That overlap
    // is the normal case for a non-interrupting event, and it stays harmless because the
    // aggregate is written column by column: see @DynamicUpdate on Aggregate.
    final var reminded = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> aggregate.getRemindersSent() != null);

    // ... and the task it is attached to is still there, so the answer is still accepted
    assertThat(reminded.getPartnerApprovalTaskId()).isEqualTo(taskId);
    assertThat(reminded.getTimedOut()).isNull();

    service.partnerApproved(loanRequestId, taskId);

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanApproval.getPartnerApproved()).isTrue();

  }

  @Test
  @DisplayName("An answer before the deadline completes the task")
  public void anAnswerInTimeIsAccepted() {

    final var loanRequestId = UUID.randomUUID().toString();
    final var taskId = startAndAwaitPartnerRequest(loanRequestId);

    service.partnerApproved(loanRequestId, taskId);

    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getCustomerInformed()));

    assertThat(loanApproval.getPartnerApproved()).isTrue();
    assertThat(loanApproval.getTimedOut()).isNull();

  }

  @Test
  @DisplayName("The interrupting event takes the task away")
  public void theInterruptingEventEndsTheWait() {

    final var loanRequestId = UUID.randomUUID().toString();

    startAndAwaitPartnerRequest(loanRequestId);

    // nobody answers: the timer boundary event fires, the workflow leaves the task and
    // takes the path behind the timer
    final var loanApproval = awaitAggregate(
        loanApprovals,
        loanRequestId,
        aggregate -> Boolean.TRUE.equals(aggregate.getTimedOut()));

    assertThat(loanApproval.getCustomerInformed()).isNull();
    // the reminders of the non-interrupting event ran before it, on the same task
    assertThat(loanApproval.getRemindersSent()).isNotNull();

    if ("camunda7".equals(System.getProperty("blueprint.bpms"))) {
      // The handler heard about it: an interrupting boundary event cancels the task, and
      // the stored id no longer leads anywhere. Not every BPMS reports that - Camunda 8
      // does not tell a worker that its job was canceled - so an application must not
      // depend on it for correctness. Completing a task that is gone is a no-op anyway.
      assertThat(loanApproval.getPartnerApprovalTaskId()).isNull();
    }

  }

}
