package blueprint.workflowmodule;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.awaitility.core.ConditionTimeoutException;

/**
 * Base class of a simulator: a stand-in for a system the workflow module talks to.
 *
 * <p>
 * A workflow module is tested by running it, which means the surrounding systems have to
 * answer. A simulator replaces one of them: it is a bean of the test context taking the
 * place of the real client, it records what it was asked to do, and it lets the test drive
 * the answer - including the answer arriving late, which is the interesting case for
 * asynchronous tasks.
 * </p>
 *
 * <p>
 * A concrete simulator subclasses this, is exposed as a {@code @TestConfiguration} bean
 * replacing the real client, calls {@link #record(String)} in every method it offers, and
 * adds the methods a test needs to make it answer.
 * </p>
 *
 * <pre>
 * &#64;TestConfiguration
 * static class Simulators {
 *   &#64;Bean
 *   &#64;Primary
 *   RatingServiceSimulator ratingService() { return new RatingServiceSimulator(); }
 * }
 * </pre>
 *
 * <p>
 * Part of the blueprint test harness: identical in every blueprint, kept in sync from
 * {@code templates/test-harness/springboot/} of the monorepo. Do not edit it here.
 * </p>
 */
public abstract class Simulator {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  private final List<String> invocations = Collections.synchronizedList(new ArrayList<>());

  /**
   * To be called by every method the simulator offers, naming what was asked of it.
   *
   * @param invocation What the simulated system was asked to do.
   */
  protected void record(
      final String invocation) {

    invocations.add(invocation);

  }

  /**
   * Waits until the simulated system was asked to do something. This is how a test learns
   * that the process reached the point where it talks to that system.
   *
   * @param invocation What the simulated system is expected to be asked for.
   */
  public void awaitInvocation(
      final String invocation) {

    try {

      await()
          .atMost(TIMEOUT)
          .pollInterval(POLL_INTERVAL)
          .until(() -> invocations.contains(invocation));

    } catch (ConditionTimeoutException e) {

      throw new AssertionError(
          "The simulated system was not asked to '"
              + invocation
              + "' within "
              + TIMEOUT
              + ". It was asked to: "
              + invocations(), e);

    }

  }

  /**
   * Everything the simulated system was asked to do, in order.
   *
   * @return The recorded invocations.
   */
  public List<String> invocations() {

    return List.copyOf(invocations);

  }

  /** Forgets all recorded invocations. To be called between two tests sharing a context. */
  public void reset() {

    invocations.clear();

  }

}
