package io.veriguard.database.repository;

import io.veriguard.database.model.combination.AttackCombinationTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackCombinationTemplateRepository
    extends CrudRepository<AttackCombinationTemplate, String>,
        JpaSpecificationExecutor<AttackCombinationTemplate> {

  Optional<AttackCombinationTemplate> findByBaseAttackTypeAndBypassDimensionId(
      String baseAttackType, String bypassDimensionId);

  List<AttackCombinationTemplate> findAllByBaseAttackType(String baseAttackType);
}
