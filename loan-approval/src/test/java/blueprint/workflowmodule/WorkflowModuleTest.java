package blueprint.workflowmodule;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;

/**
 * Base class for the integration test of a workflow module. It boots
 * {@link TestApplication} - the workflow module, a database and a BPMS adapter - and adds
 * the one thing such a test always needs: waiting for a workflow to have made progress.
 *
 * <p>
 * <strong>Assert on the workflow aggregate, never on the engine.</strong> The aggregate is
 * the state of the business case, and it is the only thing that means the same on every
 * BPMS. A test reaching into engine internals stops being a test of your application and
 * starts being a test of that engine - and it will not survive the next one.
 * </p>
 *
 * <p>
 * <strong>Wait, do not assert immediately.</strong> A BPMS runs tasks in transactions of
 * its own, and a remote one does so eventually. Asserting right after having started a
 * workflow passes on an embedded engine and fails on a remote one, which is the kind of
 * test that makes people believe the BPMS is unreliable.
 * </p>
 *
 * <p>
 * Do not annotate subclasses with {@code @Transactional}: the polling below has to see the
 * writes of the BPMS' own transactions, which a test-managed transaction would hide.
 * </p>
 *
 * <p>
 * Part of the blueprint test harness: identical in every blueprint, kept in sync from
 * {@code templates/test-harness/springboot/} of the monorepo. Do not edit it here.
 * </p>
 */
@SpringBootTest
public abstract class WorkflowModuleTest {

  /** Generous on purpose: a slow CI machine must not turn into a flaky test. */
  protected static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  /**
   * Waits until the workflow aggregate exists and satisfies the given condition.
   *
   * @param <A>        The type of the workflow aggregate.
   * @param repository The repository of the workflow aggregate.
   * @param id         The aggregate's ID, which is the natural ID of the business case.
   * @param condition  What the process is expected to have achieved.
   * @return The aggregate as it satisfied the condition.
   */
  protected <A> A awaitAggregate(
      final CrudRepository<A, String> repository,
      final String id,
      final Predicate<A> condition) {

    return awaitAggregate(repository::findById, id, condition);

  }

  /**
   * Waits until the workflow aggregate exists and satisfies the given condition, reading it
   * through a function rather than a repository.
   *
   * <p>
   * For an application whose aggregates are not managed by a repository at all: it brings a
   * persistence of its own, and then the way to read an aggregate is whatever that
   * persistence offers.
   * </p>
   *
   * @param <A>       The type of the workflow aggregate.
   * @param load      Loads the workflow aggregate by its ID.
   * @param id        The aggregate's ID, which is the natural ID of the business case.
   * @param condition What the process is expected to have achieved.
   * @return The aggregate as it satisfied the condition.
   */
  protected <A> A awaitAggregate(
      final Function<String, Optional<A>> load,
      final String id,
      final Predicate<A> condition) {

    final var lastSeen = new AtomicReference<Optional<A>>(Optional.empty());

    try {

      await()
          .atMost(TIMEOUT)
          .pollInterval(POLL_INTERVAL)
          .until(() -> {
            // A fresh read per poll - otherwise the writes of the BPMS' own transactions
            // would never become visible.
            final var found = load.apply(id);
            lastSeen.set(found);
            return found
                .filter(condition)
                .isPresent();
          });

    } catch (ConditionTimeoutException e) {

      throw new AssertionError(
          "The workflow '"
              + id
              + "' did not reach the expected state within "
              + TIMEOUT
              + ". Last seen: "
              + lastSeen
                  .get()
                  .map(Object::toString)
                  .orElse("no aggregate at all - was the workflow started?"), e);

    }

    return lastSeen
        .get()
        .orElseThrow();

  }

  /**
   * Waits until the workflow aggregate exists at all. Useful right after having started a
   * workflow.
   *
   * @param <A>        The type of the workflow aggregate.
   * @param repository The repository of the workflow aggregate.
   * @param id         The aggregate's ID.
   * @return The aggregate.
   */
  protected <A> A awaitAggregate(
      final CrudRepository<A, String> repository,
      final String id) {

    return awaitAggregate(repository, id, aggregate -> true);

  }

  /**
   * Waits until the workflow aggregate exists at all, reading it through a function rather
   * than a repository.
   *
   * @param <A>  The type of the workflow aggregate.
   * @param load Loads the workflow aggregate by its ID.
   * @param id   The aggregate's ID.
   * @return The aggregate.
   */
  protected <A> A awaitAggregate(
      final Function<String, Optional<A>> load,
      final String id) {

    return awaitAggregate(load, id, aggregate -> true);

  }

}
