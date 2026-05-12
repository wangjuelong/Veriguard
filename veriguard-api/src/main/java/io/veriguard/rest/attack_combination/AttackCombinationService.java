package io.veriguard.rest.attack_combination;

import io.veriguard.combination.transform.PayloadTransformRegistry;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import io.veriguard.database.repository.BypassDimensionRepository;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.BypassDimensionOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.BypassDimensionPageOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.CombinationPreviewOutput;
import io.veriguard.rest.attack_combination.AttackCombinationDtos.CombinationSampleOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务服务 —— IPv6 安全验证系统 §3.6 ★2 攻击组合 PR D1.
 *
 * <p>提供维度查询、组合预览两个功能：
 * <ul>
 *   <li>维度查询：支持按 category 过滤 + 分页（默认按 name 升序）</li>
 *   <li>组合预览：返回笛卡尔积 total 与前 N 条 payload 样例；
 *       PR D1 不真正生成 30 000 个实例，只在 PR D2 引入 Generator 后落地</li>
 * </ul>
 */
@Service
public class AttackCombinationService {

  /** 预览时返回的 sample 数量上限. */
  public static final int PREVIEW_SAMPLE_SIZE = 10;

  private final BypassDimensionRepository dimensionRepository;
  private final PayloadTransformRegistry transformRegistry;

  public AttackCombinationService(
      BypassDimensionRepository dimensionRepository,
      PayloadTransformRegistry transformRegistry) {
    this.dimensionRepository = dimensionRepository;
    this.transformRegistry = transformRegistry;
  }

  @Transactional(readOnly = true)
  public BypassDimensionPageOutput listDimensions(
      Optional<BypassDimensionCategory> category, int page, int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0, got " + page);
    }
    if (size < 1 || size > 500) {
      throw new IllegalArgumentException("size must be between 1 and 500, got " + size);
    }
    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));

    Page<BypassDimension> result =
        category
            .map(c -> dimensionRepository.findAllByCategory(c, pageable))
            .orElseGet(() -> dimensionRepository.findAll(pageable));

    List<BypassDimensionOutput> content =
        result.getContent().stream().map(AttackCombinationService::toOutput).toList();
    return new BypassDimensionPageOutput(
        content,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public CombinationPreviewOutput preview(
      List<String> baseAttackTypes, List<String> bypassDimensionIds, String previewBasePayload) {
    if (baseAttackTypes == null || baseAttackTypes.isEmpty()) {
      throw new IllegalArgumentException("base_attack_types must not be empty");
    }
    if (bypassDimensionIds == null || bypassDimensionIds.isEmpty()) {
      throw new IllegalArgumentException("bypass_dimension_ids must not be empty");
    }
    if (previewBasePayload == null) {
      throw new IllegalArgumentException("preview_base_payload must not be null");
    }

    // Distinct + preserve order
    List<String> distinctBaseTypes = baseAttackTypes.stream().distinct().toList();
    List<String> distinctDimIds = bypassDimensionIds.stream().distinct().toList();

    long total = (long) distinctBaseTypes.size() * (long) distinctDimIds.size();

    // Load referenced dimensions; fail-fast on missing id
    List<BypassDimension> dimensions =
        distinctDimIds.stream()
            .map(
                id ->
                    dimensionRepository
                        .findById(id)
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "Unknown bypass_dimension_id: " + id)))
            .toList();

    List<CombinationSampleOutput> samples = new ArrayList<>();
    outer:
    for (String baseType : distinctBaseTypes) {
      for (BypassDimension dim : dimensions) {
        if (samples.size() >= PREVIEW_SAMPLE_SIZE) {
          break outer;
        }
        String preview =
            transformRegistry.transform(
                dim.getTransformType(), previewBasePayload, dim.getTransformConfig());
        samples.add(
            new CombinationSampleOutput(baseType, dim.getId(), dim.getName(), preview));
      }
    }

    return new CombinationPreviewOutput(
        distinctBaseTypes,
        distinctDimIds,
        total,
        samples.size(),
        samples,
        previewBasePayload);
  }

  private static BypassDimensionOutput toOutput(BypassDimension dim) {
    return new BypassDimensionOutput(
        dim.getId(),
        dim.getName(),
        dim.getCategory() == null ? null : dim.getCategory().name(),
        dim.getDescription(),
        dim.getTransformType(),
        dim.getTransformConfig(),
        dim.getCreatedAt(),
        dim.getUpdatedAt());
  }
}
