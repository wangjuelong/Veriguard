package io.veriguard.database.specification;

import io.veriguard.database.model.Document;
import jakarta.persistence.criteria.Path;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.domain.Specification;

public class DocumentSpecification {

  private DocumentSpecification() {}

  public static Specification<Document> findGrantedFor(@NotBlank final String userId) {
    return (root, query, criteriaBuilder) -> {
      Path<Object> attackChainRunPath =
          root.join("exercises").join("grants").join("group").join("users").get("id");

      Path<Object> attackChainPath =
          root.join("scenarios").join("grants").join("group").join("users").get("id");

      return criteriaBuilder.or(
          criteriaBuilder.equal(attackChainRunPath, userId), criteriaBuilder.equal(attackChainPath, userId));
    };
  }
}
