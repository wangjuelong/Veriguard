package io.veriguard.database.repository;

import io.veriguard.database.model.coverage.Policy;
import io.veriguard.database.model.coverage.PolicyDeviceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 安全设备策略仓储 —— PR C3 边界覆盖度子模块.
 */
@Repository
public interface PolicyRepository
    extends CrudRepository<Policy, String>, JpaSpecificationExecutor<Policy> {

  Page<Policy> findAll(Pageable pageable);

  Page<Policy> findAllByDeviceType(PolicyDeviceType deviceType, Pageable pageable);

  long countByDeviceType(PolicyDeviceType deviceType);
}
