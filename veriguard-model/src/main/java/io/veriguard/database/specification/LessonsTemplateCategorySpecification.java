package io.veriguard.database.specification;

import io.veriguard.database.model.LessonsTemplateCategory;
import org.springframework.data.jpa.domain.Specification;

public class LessonsTemplateCategorySpecification {

  public static Specification<LessonsTemplateCategory> fromTemplate(String templateId) {
    return (root, query, cb) -> cb.equal(root.get("template").get("id"), templateId);
  }
}
