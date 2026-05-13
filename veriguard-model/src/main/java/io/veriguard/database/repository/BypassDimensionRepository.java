package io.veriguard.database.repository;

import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BypassDimensionRepository
    extends CrudRepository<BypassDimension, String>, JpaSpecificationExecutor<BypassDimension> {

  Optional<BypassDimension> findByName(String name);

  List<BypassDimension> findAllByCategory(BypassDimensionCategory category);

  Page<BypassDimension> findAllByCategory(BypassDimensionCategory category, Pageable pageable);

  Page<BypassDimension> findAll(Pageable pageable);

  long count();
}
