package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import io.vanillabp.spi.process.ProcessService;

/**
 * The smoke test of the application: does the context start, is every workflow module
 * found, and is every BPMN task wired to code?
 *
 * <p>
 * Booting is the bigger half of the assertion. VanillaBP validates the wiring between BPMN
 * and code while the application starts, so a context which comes up means every BPMN task
 * has its {@code @WorkflowTask} method and the other way round. A failing start is a real
 * finding, and its message says what is missing.
 * </p>
 *
 * <p>
 * The explicit assertion covers the case booting cannot: an application that starts
 * perfectly well because it contains no workflow module at all - a JAR left out of the
 * dependencies, or a marker file missing after a rename.
 * </p>
 *
 * <p>
 * A smoke test exists in addition to the tests of the workflow modules because several
 * modules in one runtime can interfere in ways an isolated module test never sees.
 * </p>
 *
 * <p>
 * Part of the blueprint test harness: identical in every blueprint, kept in sync from
 * {@code templates/test-harness/springboot/} of the monorepo. Do not edit it here.
 * </p>
 */
@SpringBootTest
public class ApplicationSmokeTest {

  @Autowired
  private ApplicationContext context;

  @Test
  public void theApplicationStartsAndEveryWorkflowIsWired() {

    assertThat(context.getBeanNamesForType(ProcessService.class))
        .describedAs(
            "No ProcessService bean exists, so no workflow module was detected."
                + " Check the module's dependency and its META-INF/workflow-module file.")
        .isNotEmpty();

  }

}
