package io.veriguard.database.specification;

import io.veriguard.database.model.Tag;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;

public class TagSpecification {

  private TagSpecification() {}

  public static Specification<Tag> byName(@Nullable final String searchText) {
    return UtilsSpecification.byName(searchText, "name");
  }
}
