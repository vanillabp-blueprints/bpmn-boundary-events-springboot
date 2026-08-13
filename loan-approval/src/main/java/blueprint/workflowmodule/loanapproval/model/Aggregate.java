package blueprint.workflowmodule.loanapproval.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The workflow aggregate: one entity per workflow instance, holding everything the
 * process needs to know. There are no process variables - this is the single source of
 * truth, and it stays a normal JPA entity your application can use like any other.
 *
 * <p>
 * The attribute this blueprint is about is {@link #partnerApprovalTaskId}. A task waiting
 * for a surrounding system may wait for days, so something has to remember which task the
 * answer belongs to. The aggregate is where that belongs: it is transactional, it is
 * queryable, and it survives a restart of both sides.
 * </p>
 *
 * @see <a href=
 *      "https://github.com/vanillabp/adapter-platform-integration/wiki/Workflow-aggregates">Workflow
 *      aggregates</a>
 */
@Entity
@Table(name = "LOAN_APPROVAL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aggregate {

  /**
   * The natural id of the use case. Using a business identifier instead of a generated
   * one makes a workflow started twice for the same business case a detectable
   * duplicate.
   *
   * @see <a href="https://github.com/vanillabp/spi-for-java#natural-ids">Natural ids</a>
   */
  @Id
  private String loanRequestId;

  /** The amount requested. */
  @Column
  private Integer amount;

  /** Filled by the business code the first service task of the process triggers. */
  @Column
  private Integer creditRating;

  /**
   * The id of the task waiting for the partner's answer, reported by the BPMS when the
   * task was delivered. It is the handle needed to complete that task, and it is null
   * whenever nothing is waiting - which the deadline is what makes true again.
   */
  @Column
  private String partnerApprovalTaskId;

  /** Whether the partner answered, written when their answer arrives. */
  @Column
  private Boolean partnerApproved;

  /** Written by the service task following the completed task. */
  @Column
  private Boolean customerInformed;

  /** Written by the service task the interrupting boundary event leads to. */
  @Column
  private Boolean timedOut;

  /**
   * How many reminders went out while the task was open. The non-interrupting boundary
   * event increments it, and that the task is still open afterwards is the whole point of
   * the difference between the two events.
   */
  @Column
  private Integer remindersSent;

}
