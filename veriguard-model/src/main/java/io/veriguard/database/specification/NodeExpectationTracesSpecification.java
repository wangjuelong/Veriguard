package io.veriguard.database.specification;

import io.veriguard.database.model.NodeExpectationTrace;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.data.jpa.domain.Specification;

public class NodeExpectationTracesSpecification {

  public static Specification<NodeExpectationTrace> afterAlertDate(@NotBlank final Instant date) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("alertDate"), date);
  }
}
