package blueprint.workflowmodule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
 * What booting cannot notice is a workflow module which is simply not there: a JAR left out
 * of the dependencies, a marker file missing after a rename, or a module whose beans never
 * reached the application. The application starts perfectly well without it. So the test
 * counts: every workflow module declaring itself on the classpath has to be served by a
 * {@code ProcessService}, and the modules are read from the same marker files VanillaBP
 * reads.
 * </p>
 *
 * <p>
 * An application which is its own workflow module has no marker file at all. There is
 * nothing to count then, and the assertion falls back to what it can say: something has to
 * be wired.
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

  /** The file a workflow module declares itself with; it contains the module's ID. */
  private static final String WORKFLOW_MODULE_MARKER = "META-INF/workflow-module";

  @Autowired
  private ApplicationContext context;

  @Test
  public void theApplicationStartsAndEveryWorkflowModuleIsWired() throws Exception {

    final var declared = declaredWorkflowModules();
    final var wired = context
        .getBeansOfType(ProcessService.class)
        .values()
        .stream()
        .map(ProcessService::getWorkflowModuleId)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    if (declared.isEmpty()) {

      assertThat(wired)
          .describedAs(
              "No ProcessService bean exists, so no workflow module was detected."
                  + " Check the module's dependency and its "
                  + WORKFLOW_MODULE_MARKER
                  + " file.")
          .isNotEmpty();
      return;

    }

    assertThat(wired)
        .describedAs(
            "Every workflow module on the classpath has to be served by a ProcessService."
                + " Declared: "
                + declared
                + ", wired: "
                + wired
                + ". A module which is declared but not wired is missing its beans;"
                + " one which is wired but not declared has no "
                + WORKFLOW_MODULE_MARKER
                + " file.")
        .containsExactlyInAnyOrderElementsOf(declared);

  }

  /**
   * The workflow modules which declare themselves on the classpath, read from the marker
   * files VanillaBP reads.
   *
   * @return The module IDs, empty if the application is its own workflow module.
   * @throws Exception If a marker file cannot be read.
   */
  private static Set<String> declaredWorkflowModules() throws Exception {

    final var modules = new LinkedHashSet<String>();
    final var markers = Thread
        .currentThread()
        .getContextClassLoader()
        .getResources(WORKFLOW_MODULE_MARKER);

    while (markers.hasMoreElements()) {
      try (var reader = new BufferedReader(
          new InputStreamReader(
              markers
                  .nextElement()
                  .openStream(), StandardCharsets.UTF_8))) {
        reader
            .lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .forEach(modules::add);
      }
    }

    return modules;

  }

}
