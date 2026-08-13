package blueprint.workflowmodule.loanapproval;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * A stand-in for the real partner, so the blueprint runs without one. Replace it by an
 * HTTP client, a queue producer or whatever the partner speaks.
 *
 * <p>
 * It does not answer. A real partner would not either, at least not within this call -
 * the answer arrives later at the API, which is exactly what the logged URLs stand in
 * for.
 * </p>
 */
@Slf4j
@Component
public class LocalPartnerApprovalClient implements PartnerApprovalClient {

  @Override
  public void requestApproval(
      final String loanRequestId,
      final int amount,
      final int creditRating) {

    log.info(
        "Partner was asked to approve loan approval '{}' ({} at a rating of {})",
        loanRequestId,
        amount,
        creditRating);

  }

}
