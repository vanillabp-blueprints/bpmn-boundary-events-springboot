package blueprint.workflowmodule;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A workflow module is a JAR and cannot be started on its own, so testing it means
 * bringing a minimal application along. This is that application: it exists only in the
 * test sources and does nothing but boot the module together with a database and a BPMS
 * adapter.
 *
 * <p>
 * It lives in the base package so that component scanning, entity scanning and Spring
 * Data repositories of the workflow module are found without configuration - exactly as
 * the real application does it.
 * </p>
 *
 * <p>
 * Part of the test harness which every blueprint carries in the same form. In a project
 * made from a blueprint it is a file of that project, so change it as the project needs.
 * </p>
 */
@SpringBootApplication
public class TestApplication {
}
