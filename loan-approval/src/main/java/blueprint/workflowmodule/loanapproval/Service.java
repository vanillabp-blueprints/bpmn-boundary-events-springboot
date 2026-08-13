package blueprint.workflowmodule.loanapproval;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.Transactional;

import blueprint.workflowmodule.loanapproval.config.LoanApprovalProperties;
import blueprint.workflowmodule.loanapproval.model.Aggregate;
import blueprint.workflowmodule.loanapproval.model.AggregateRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * The business service of this use case: what the application can do with a loan
 * approval, expressed without a single word about processes.
 *
 * <p>
 * It never touches VanillaBP. Whenever the business case moves on, it tells
 * {@link Workflow} what happened, {@code partnerApproved} rather than "complete the
 * task", and that class decides what this means for the BPMN. The other direction runs
 * through {@link WorkflowTaskHandler}.
 * </p>
 *
 * <p>
 * An asynchronous task splits its handling in two, and both halves are here: the process
 * asks for the partner's approval ({@link #requestPartnerApproval}), and much later the
 * answer arrives through the API ({@link #partnerDecided}). Nothing keeps the two
 * together but the task id on the workflow aggregate.
 * </p>
 *
 * <p>
 * Note where {@code @Transactional} sits. It is on the methods the API calls, because
 * starting a workflow and answering a task have to run in a transaction. It is
 * deliberately absent from the methods a task handler calls: VanillaBP already runs a
 * task in a transaction it owns, and a transaction declared here would break the
 * guarantees that come with it. VanillaBP sees such a transaction and fails the task
 * naming it, so the mistake shows up rather than costing data.
 * </p>
 */
@Slf4j
@org.springframework.stereotype.Service
@EnableConfigurationProperties(LoanApprovalProperties.class)
public class Service {

  @Autowired
  private AggregateRepository loanApprovals;

  @Autowired
  private Workflow workflow;

  @Autowired
  private PartnerApprovalClient partner;

  @Autowired
  private LoanApprovalProperties properties;

  /**
   * A customer requests a loan.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   */
  @Transactional
  public void initiateLoanApproval(
      final String loanRequestId,
      final int amount) {

    final var loanApproval = Aggregate
        .builder()
        .loanRequestId(loanRequestId)
        .amount(amount)
        .build();

    workflow.loanRequested(loanApproval);

    log.info("Loan approval '{}' started", loanRequestId);

  }

  /**
   * Rates a loan request, which is what the service task ahead of the partner's approval
   * triggers.
   *
   * @param loanApproval The loan approval to rate.
   */
  public void assessCreditRating(
      final Aggregate loanApproval) {

    final var rating = Math.min(
        properties.getRatingScale(),
        loanApproval.getAmount() / 100);

    loanApproval.setCreditRating(rating);

    log.info(
        "Credit rating of loan approval '{}' is {}",
        loanApproval.getLoanRequestId(),
        rating);

  }

  /**
   * Asks the partner to approve the loan and remembers which task their answer belongs
   * to. Keeping that id is what makes the answer possible at all, because it is the only
   * way back to a task that stays open.
   *
   * <p>
   * A remote BPMS may deliver the same task twice, and asking a partner twice may create
   * two cases on their side. The stored id is what tells the two apart: if it is there,
   * the request went out already.
   * </p>
   *
   * @param loanApproval The workflow's aggregate.
   * @param taskId       The id of the task just delivered.
   */
  public void requestPartnerApproval(
      final Aggregate loanApproval,
      final String taskId) {

    if (loanApproval.getPartnerApprovalTaskId() != null) {

      log.info(
          "The partner was asked for loan approval '{}' already, nothing to do",
          loanApproval.getLoanRequestId());
      return;

    }

    loanApproval.setPartnerApprovalTaskId(taskId);

    partner.requestApproval(
        loanApproval.getLoanRequestId(),
        loanApproval.getAmount(),
        loanApproval.getCreditRating());

    log.info(
        "Loan approval '{}' waits for the partner, but not forever - the task carries a"
            + " deadline of ten seconds, and reminders go out while it runs:"
            + "\n  Approved -> http://localhost:8080/api/loan-approval/{}/partner-approved/{}",
        loanApproval.getLoanRequestId(),
        loanApproval.getLoanRequestId(), taskId);

  }

  /**
   * The task is gone without an answer: the deadline on it ran out and the workflow took
   * it away. Whatever was set up
   * when it was delivered is torn down here, and the stored id is dropped because it does
   * not lead anywhere any more.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void partnerApprovalClosed(
      final Aggregate loanApproval) {

    loanApproval.setPartnerApprovalTaskId(null);

    log.info(
        "The partner request of loan approval '{}' was canceled",
        loanApproval.getLoanRequestId());

  }

  /**
   * The partner answered. This is what the process waits for, and it arrives through the
   * API rather than through the BPMS.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the task being answered.
   */
  @Transactional
  public void partnerApproved(
      final String loanRequestId,
      final String taskId) {

    final var loanApproval = openPartnerRequest(loanRequestId, taskId);

    loanApproval.setPartnerApproved(true);

    workflow.partnerApproved(loanApproval, taskId);

    // The task is answered, so the id does not lead to an open task any more.
    loanApproval.setPartnerApprovalTaskId(null);

    log.info("The partner approved loan approval '{}'", loanRequestId);

  }

  /**
   * Tells the customer that their loan was approved, which is what the service task
   * behind the answered task triggers.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void informCustomer(
      final Aggregate loanApproval) {

    loanApproval.setCustomerInformed(true);

    log.info(
        "The customer of loan approval '{}' was informed",
        loanApproval.getLoanRequestId());

  }

  /**
   * Sends a reminder, which is what the NON-INTERRUPTING boundary event triggers. The task
   * is still open while this runs: nothing was canceled, the workflow only did something
   * on the side.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void sendReminder(
      final Aggregate loanApproval) {

    final var reminders = loanApproval.getRemindersSent() == null
        ? 1
        : loanApproval.getRemindersSent() + 1;
    loanApproval.setRemindersSent(reminders);

    log.info(
        "Reminder {} for loan approval '{}' - the partner is still expected to answer",
        reminders,
        loanApproval.getLoanRequestId());

  }

  /**
   * Notes that nobody answered in time, which is what the service task behind the timer
   * triggers. Whatever the process does from here is a business decision - this blueprint
   * ends, a real one might ask somebody else.
   *
   * @param loanApproval The workflow's aggregate.
   */
  public void noteTimeout(
      final Aggregate loanApproval) {

    loanApproval.setTimedOut(true);

    log.info(
        "Nobody answered for loan approval '{}' in time",
        loanApproval.getLoanRequestId());

  }

  /**
   * The state of a loan approval, as far as the process has come.
   *
   * @param loanRequestId The natural id of the loan request.
   * @return The loan approval, if it exists.
   */
  public Optional<Aggregate> getLoanApproval(
      final String loanRequestId) {

    return loanApprovals.findById(loanRequestId);

  }

  /**
   * The loan approval whose open task is the given one, refusing anything else. An answer
   * arrives from outside and may arrive twice, late, or for a task the workflow has taken
   * away in the meantime - all of which is rejected here rather than being sent to the
   * BPMS.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param taskId        The id of the task expected to be open.
   * @return The loan approval.
   */
  private Aggregate openPartnerRequest(
      final String loanRequestId,
      final String taskId) {

    final var loanApproval = loanApprovals
        .findById(loanRequestId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown loan request '"
            + loanRequestId
            + "'"));

    if (!taskId.equals(loanApproval.getPartnerApprovalTaskId())) {

      throw new IllegalStateException("The partner request '"
          + taskId
          + "' of loan approval '"
          + loanRequestId
          + "' is not open any more");

    }

    return loanApproval;

  }

}
