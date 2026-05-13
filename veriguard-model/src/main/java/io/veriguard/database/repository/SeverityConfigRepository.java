package io.veriguard.database.repository;

import io.veriguard.database.model.combination.SeverityConfig;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 分级配置仓储 —— IPv6 安全验证系统 §3.6 ★2 PR D4.
 *
 * <p>Singleton 表：最多一行；应用层通过固定 {@link SeverityConfig#SINGLETON_ID} 寻址。
 */
@Repository
public interface SeverityConfigRepository extends CrudRepository<SeverityConfig, String> {

  /** 返回 singleton 行，若不存在则空（用 default value 兜底） */
  default Optional<SeverityConfig> findSingleton() {
    return findById(SeverityConfig.SINGLETON_ID);
  }
}
