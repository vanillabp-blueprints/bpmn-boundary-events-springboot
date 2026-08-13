package blueprint.workflowmodule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application. It contains no business code at all - it pulls in the workflow module
 * and decides, by its Maven dependencies, which BPMS adapter is loaded.
 *
 * <p>
 * Its package is the parent package of the workflow module, so component scanning,
 * entity scanning and Spring Data repositories of the module are picked up without any
 * configuration.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
