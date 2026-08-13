package blueprint.workflowmodule.loanapproval.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AggregateRepository extends JpaRepository<Aggregate, String> {
}
