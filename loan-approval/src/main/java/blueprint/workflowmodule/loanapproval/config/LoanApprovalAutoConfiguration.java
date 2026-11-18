package blueprint.workflowmodule.loanapproval.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import blueprint.workflowmodule.loanapproval.Service;

/**
 * The workflow module wires itself. An application adds the module as a Maven dependency, and
 * that is all it has to do.
 *
 * <p>
 * A Spring Boot application scans its own package and the packages below it. The application
 * of this blueprint is in {@code blueprint.application}, which is not above the module. In a
 * project the module may come from another team, in a package the application does not know.
 * So the module says once what it brings:
 * </p>
 *
 * <ul>
 * <li>{@code @ComponentScan} finds the beans of the module. It leaves out the
 * auto-configurations of the module, because Spring Boot loads them itself.</li>
 * <li>{@code @EntityScan} and {@code @EnableJpaRepositories} find the aggregate and the
 * repository which reads it.</li>
 * </ul>
 *
 * <p>
 * Every scan names the package of the module by {@link Service}. Without it, a scan would
 * only search {@code config}, the package of this class.
 * </p>
 *
 * <p>
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * which Spring Boot reads from every JAR on the class path.
 * </p>
 */
@AutoConfiguration
@ComponentScan(
    basePackageClasses = Service.class,
    excludeFilters = @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class))
@EntityScan(basePackageClasses = Service.class)
@EnableJpaRepositories(basePackageClasses = Service.class)
public class LoanApprovalAutoConfiguration {
}
