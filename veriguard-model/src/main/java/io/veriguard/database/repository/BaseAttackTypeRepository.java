package io.veriguard.database.repository;

import io.veriguard.database.model.combination.BaseAttackType;
import io.veriguard.database.model.combination.BaseAttackTypeCategory;
import io.veriguard.database.model.combination.BaseAttackTypeTargetLayer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BaseAttackTypeRepository
    extends CrudRepository<BaseAttackType, String>, JpaSpecificationExecutor<BaseAttackType> {

  Optional<BaseAttackType> findByName(String name);

  List<BaseAttackType> findAllByCategory(BaseAttackTypeCategory category);

  List<BaseAttackType> findAllByTargetLayer(BaseAttackTypeTargetLayer targetLayer);

  Page<BaseAttackType> findAllByCategory(BaseAttackTypeCategory category, Pageable pageable);

  Page<BaseAttackType> findAllByTargetLayer(
      BaseAttackTypeTargetLayer targetLayer, Pageable pageable);

  Page<BaseAttackType> findAllByCategoryAndTargetLayer(
      BaseAttackTypeCategory category,
      BaseAttackTypeTargetLayer targetLayer,
      Pageable pageable);

  Page<BaseAttackType> findAll(Pageable pageable);

  long count();
}
