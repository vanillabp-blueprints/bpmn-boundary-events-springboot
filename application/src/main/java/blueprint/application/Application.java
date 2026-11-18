package blueprint.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application. It contains no business code at all - it pulls in the workflow module
 * and decides, by its Maven dependencies, which BPMS adapter is loaded.
 *
 * <p>
 * Its package is not above the package of the workflow module, so its component scan does
 * not reach the module. The module wires itself with an auto-configuration, as it has to when
 * it comes from another team as a JAR. So this class names nothing of the module.
 * </p>
 */
@SpringBootApplication
public class Application {

  public static void main(
      String[] args) {

    SpringApplication.run(Application.class, args);

  }

}
