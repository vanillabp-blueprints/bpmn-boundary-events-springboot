package blueprint.workflowmodule.loanapproval;

/**
 * The surrounding system the asynchronous task talks to. It is an interface owned by this
 * workflow module, so a test can put a simulator in its place and the process can be
 * played through without a partner being available.
 *
 * <p>
 * Its method returns nothing on purpose. The partner answers later, through the
 * application's API, and that is the whole point of an asynchronous task: sending the
 * request and receiving the answer are two separate events, and nothing blocks in
 * between.
 * </p>
 */
public interface PartnerApprovalClient {

  /**
   * Asks the partner to approve a loan request.
   *
   * @param loanRequestId The natural id of the loan request.
   * @param amount        The amount requested.
   * @param creditRating  What the rating step concluded.
   */
  void requestApproval(
      String loanRequestId,
      int amount,
      int creditRating);

}
