package blueprint.workflowmodule.loanapproval;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import blueprint.workflowmodule.loanapproval.model.Aggregate;
import io.vanillabp.spi.service.BpmnProcess;
import io.vanillabp.spi.service.TaskEvent;
import io.vanillabp.spi.service.TaskId;
import io.vanillabp.spi.service.WorkflowService;
import io.vanillabp.spi.service.WorkflowTask;

/**
 * What the process tells the application: the incoming half of the BPMN wiring.
 *
 * <p>
 * This is a driving adapter, the same kind of thing as {@link ApiController}: something
 * outside triggers, and the trigger is translated into a call to {@link Service}. That
 * the caller is a BPMS rather than a browser changes nothing about the direction.
 * </p>
 *
 * <p>
 * There is no {@code @Transactional} here, and adding one would be a mistake. VanillaBP
 * loads the aggregate, runs the method and saves the aggregate in one transaction it
 * owns. A transaction declared by the application would take that guarantee away, which
 * is why such an annotation on this class or on a {@code @WorkflowTask} method fails the
 * boot naming the method.
 * </p>
 *
 * @see <a href="https://github.com/vanillabp/spi-for-java#wire-up-a-task">Wire up a task</a>
 */
@Component
@WorkflowService(
    workflowAggregateClass = Aggregate.class,
    bpmnProcess = @BpmnProcess(bpmnProcessId = "loan_approval"))
public class WorkflowTaskHandler {

  @Autowired
  private Service loanApproval;

  /**
   * Called by VanillaBP when the BPMN service task of the same name is reached. The
   * aggregate is loaded before and saved after the call, so the business code only has to
   * change it.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void retrieveCreditRating(
      final Aggregate loanRequest) {

    loanApproval.assessCreditRating(loanRequest);

  }

  /**
   * Called by VanillaBP when the task waiting for the partner is reached. The
   * {@code @TaskId} parameter is what turns this into an asynchronous task: returning
   * from the method does NOT complete it, and the process stays here until the
   * application says otherwise. Without that parameter the very same method would
   * complete the task by returning.
   *
   * <p>
   * The same method is called again if the workflow takes the task away, which
   * {@code @TaskEvent} tells apart: {@code CREATED} on delivery, {@code CANCELED} when an
   * interrupting boundary event - the deadline of this blueprint - or the end of the
   * workflow removed it. A method without a {@code @TaskEvent} parameter never
   * hears about that, and would keep a task id nobody can answer any more.
   * </p>
   *
   * <p>
   * A remote BPMS may deliver the same task more than once, so this method has to be
   * idempotent - which is why {@link Service} decides whether the partner is asked, not
   * this class.
   * </p>
   *
   * @param loanRequest The workflow's aggregate.
   * @param taskId       The BPMS-side id of this task.
   * @param event        Whether the task was delivered or canceled.
   */
  @WorkflowTask
  public void requestPartnerApproval(
      final Aggregate loanRequest,
      @TaskId final String taskId,
      @TaskEvent final TaskEvent.Event event) {

    switch (event) {
      case CREATED -> loanApproval.requestPartnerApproval(loanRequest, taskId);
      case CANCELED -> loanApproval.partnerApprovalClosed(loanRequest);
      default -> throw new IllegalStateException("Unexpected task event '"
          + event
          + "'");
    }

  }

  /**
   * Called by VanillaBP when the completed task was followed by the service task of the
   * same name.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void informCustomer(
      final Aggregate loanRequest) {

    loanApproval.informCustomer(loanRequest);

  }

  /**
   * Called by VanillaBP on the path the NON-INTERRUPTING boundary event leads to. The task
   * this event is attached to is untouched, so nothing here may assume the wait is over.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void sendReminder(
      final Aggregate loanRequest) {

    loanApproval.sendReminder(loanRequest);

  }

  /**
   * Called by VanillaBP on the path the INTERRUPTING boundary event leads to.
   *
   * @param loanRequest The workflow's aggregate.
   */
  @WorkflowTask
  public void noteTimeout(
      final Aggregate loanRequest) {

    loanApproval.noteTimeout(loanRequest);

  }

}
