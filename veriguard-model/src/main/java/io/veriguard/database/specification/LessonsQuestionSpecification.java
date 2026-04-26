package io.veriguard.database.specification;

import io.veriguard.database.model.LessonsQuestion;
import org.springframework.data.jpa.domain.Specification;

public class LessonsQuestionSpecification {

  public static Specification<LessonsQuestion> fromCategory(String categoryId) {
    return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
  }
}
