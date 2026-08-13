package blueprint.workflowmodule.loanapproval;

import blueprint.workflowmodule.Simulator;

/**
 * The partner, as far as the test is concerned. It stands in for the surrounding system
 * so the workflow module can be run without one, and it records what it was asked for -
 * which is how a test learns that the process reached the task.
 *
 * <p>
 * It answers nothing. The answer of an asynchronous task does not come back through this
 * client, it arrives later at the application's API, and that is what the tests do.
 * </p>
 *
 * @see Simulator
 */
public class PartnerApprovalSimulator extends Simulator implements PartnerApprovalClient {

  @Override
  public void requestApproval(
      final String loanRequestId,
      final int amount,
      final int creditRating) {

    record("approve "
        + loanRequestId);

  }

}
