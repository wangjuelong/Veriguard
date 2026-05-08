package io.veriguard.rest.report.specification;

import io.veriguard.rest.report.model.Report;
import org.springframework.data.jpa.domain.Specification;

public class ReportSpecification {

  public static Specification<Report> fromAttackChainRun(String attackChainRunId) {
    return (root, query, cb) -> cb.equal(root.get("attackChainRun").get("id"), attackChainRunId);
  }
}
