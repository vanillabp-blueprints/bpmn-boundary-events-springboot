package blueprint.workflowmodule.loanapproval;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import blueprint.workflowmodule.loanapproval.api.LoanApprovalApi;
import lombok.extern.slf4j.Slf4j;

/**
 * The API of this use case. It consists of GET requests only, so the process can be
 * walked through in a browser - no tooling, no request bodies.
 *
 * <p>
 * The endpoint carrying the partner's answer is the callback a real partner would call.
 * That it happens to be a browser here changes nothing about its shape: an id, an answer,
 * and no knowledge of the BPMS.
 * </p>
 *
 * <p>
 * The paths, the parameters and the answers come from {@link LoanApprovalApi}, the interface
 * the build generates from {@code src/main/resources/openapi/loan-approval.yaml}. This class
 * only implements it.
 * </p>
 */
@Slf4j
@RestController
public class ApiController implements LoanApprovalApi {

  @Autowired
  private Service loanApproval;

  /**
   * Starts a loan approval. This is the one URL to remember; the URLs carrying the
   * partner's answer are logged once the request went out.
   *
   * @param amount The amount requested.
   * @return The id of the loan request started.
   */
  @Override
  public String start(
      final Integer amount) {

    final var loanRequestId = UUID.randomUUID().toString();

    loanApproval.request(loanRequestId, amount);

    log.info(
        "Show the result -> http://localhost:8080/api/loan-approval/{}",
        loanRequestId);

    return loanRequestId;

  }

  /**
   * The partner's answer, which completes the waiting task - if it arrives before the
   * deadline on that task.
   *
   * @param loanRequestId The id returned by starting the process.
   * @param taskId        The id of the waiting task, taken from the logged URL.
   * @return What was done, for the browser to show.
   */
  @Override
  public String partnerApproved(
      final String loanRequestId,
      final String taskId) {

    loanApproval.partnerApproved(loanRequestId, taskId);

    return "The partner approved loan approval '"
        + loanRequestId
        + "'";

  }

  /**
   * Shows what the process did, which is the second half of operating it in a browser.
   *
   * @param loanRequestId The id returned by starting the process.
   * @return The workflow aggregate as it is stored right now.
   */
  @Override
  public String show(
      final String loanRequestId) {

    return loanApproval
        .get(loanRequestId)
        .map(Object::toString)
        .orElse("unknown loan request '"
            + loanRequestId
            + "'");

  }

}
