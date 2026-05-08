package io.veriguard.database.repository;

import io.veriguard.database.model.RuleAttribute;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleAttributeRepository extends CrudRepository<RuleAttribute, UUID> {}
