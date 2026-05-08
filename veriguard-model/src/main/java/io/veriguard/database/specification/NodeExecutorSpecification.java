package io.veriguard.database.specification;

import io.veriguard.database.model.NodeExecutor;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;

public class NodeExecutorSpecification {

  private NodeExecutorSpecification() {}

  public static Specification<NodeExecutor> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
