package io.veriguard.rest.report.specification;

import io.veriguard.rest.report.model.Report;
import org.springframework.data.jpa.domain.Specification;

public class ReportSpecification {

  public static Specification<Report> fromExercise(String exerciseId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), exerciseId);
  }
}
