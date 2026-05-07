package io.veriguard.database.specification;

import io.veriguard.database.model.LessonsCategory;
import org.springframework.data.jpa.domain.Specification;

public class LessonsCategorySpecification {

  public static Specification<LessonsCategory> fromAttackChainRun(String attackChainRunId) {
    return (root, query, cb) -> cb.equal(root.get("attackChainRun").get("id"), attackChainRunId);
  }

  public static Specification<LessonsCategory> fromAttackChain(String attackChainId) {
    return (root, query, cb) -> cb.equal(root.get("attackChain").get("id"), attackChainId);
  }
}
