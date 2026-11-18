package blueprint.workflowmodule;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * A workflow module is a JAR and cannot be started on its own, so testing it means
 * bringing a minimal application along. This is that application: it exists only in the
 * test sources and does nothing but boot the module together with a database and a BPMS
 * adapter.
 *
 * <p>
 * It scans nothing. The workflow module brings an auto-configuration, and that is how an
 * application gets its beans. {@code @EnableAutoConfiguration} applies it, together with the
 * database, the transaction manager and the BPMS adapter the test needs. A component scan
 * here would test a wiring no application uses. A test which needs a bean of the test
 * sources names it with {@code @Import}.
 * </p>
 *
 * <p>
 * Part of the test harness which every blueprint carries in the same form. In a project
 * made from a blueprint it is a file of that project, so change it as the project needs.
 * </p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {
}
