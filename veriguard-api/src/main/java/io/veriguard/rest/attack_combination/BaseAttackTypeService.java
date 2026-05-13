package io.veriguard.rest.attack_combination;

import io.veriguard.database.model.combination.BaseAttackType;
import io.veriguard.database.model.combination.BaseAttackTypeCategory;
import io.veriguard.database.model.combination.BaseAttackTypeTargetLayer;
import io.veriguard.database.repository.BaseAttackTypeRepository;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.BaseAttackTypeOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.BaseAttackTypePageOutput;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基础攻击类型查询 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR B5.
 *
 * <p>提供分页 / 按 category / target_layer 过滤的列表查询，以及按 name 取详情。
 * 前端 D5 任务编辑器可改用此 API 替代硬编码 11 类基础攻击型，覆盖招标 ≥ 250 类要求。
 */
@Service
public class BaseAttackTypeService {

  private final BaseAttackTypeRepository repository;

  public BaseAttackTypeService(BaseAttackTypeRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public BaseAttackTypePageOutput list(
      Optional<BaseAttackTypeCategory> category,
      Optional<BaseAttackTypeTargetLayer> targetLayer,
      int page,
      int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0, got " + page);
    }
    if (size < 1 || size > 500) {
      throw new IllegalArgumentException("size must be between 1 and 500, got " + size);
    }
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

    Page<BaseAttackType> result;
    if (category.isPresent() && targetLayer.isPresent()) {
      result =
          repository.findAllByCategoryAndTargetLayer(category.get(), targetLayer.get(), pageable);
    } else if (category.isPresent()) {
      result = repository.findAllByCategory(category.get(), pageable);
    } else if (targetLayer.isPresent()) {
      result = repository.findAllByTargetLayer(targetLayer.get(), pageable);
    } else {
      result = repository.findAll(pageable);
    }

    List<BaseAttackTypeOutput> content =
        result.getContent().stream().map(BaseAttackTypeService::toOutput).toList();
    return new BaseAttackTypePageOutput(
        content,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public Optional<BaseAttackTypeOutput> findByName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    return repository.findByName(name).map(BaseAttackTypeService::toOutput);
  }

  static BaseAttackTypeOutput toOutput(BaseAttackType t) {
    return new BaseAttackTypeOutput(
        t.getId(),
        t.getName(),
        t.getCategory() == null ? null : t.getCategory().name(),
        t.getDisplayLabel(),
        t.getDescription(),
        t.getSeverityScore(),
        t.getDefaultPayload(),
        t.getAttackPatternId(),
        t.getTargetLayer() == null ? null : t.getTargetLayer().name(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }
}
