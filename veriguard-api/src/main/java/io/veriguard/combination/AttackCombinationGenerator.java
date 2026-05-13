package io.veriguard.combination;

import io.veriguard.combination.transform.PayloadTransformRegistry;
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.BypassDimension;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * 攻击组合实例懒生成器 —— IPv6 安全验证系统 §3.6 ★2 PR D2.
 *
 * <p>对一个 run 执行 base × dim × asset 的笛卡尔积，按需 transform 出 payload，
 * <strong>流式</strong>产出 {@link CombinationInstance}. 30 000+ 组合 × 100 资产
 * 不会一次性 materialize，由消费方（scheduler）拉取多少生成多少.
 *
 * <p>顺序：base 外层 → dim 中层 → asset 内层；与前端预览顺序一致.
 */
@Component
public class AttackCombinationGenerator {

  private final PayloadTransformRegistry transformRegistry;
  private final BaseAttackPayloadCatalog payloadCatalog;

  public AttackCombinationGenerator(
      PayloadTransformRegistry transformRegistry, BaseAttackPayloadCatalog payloadCatalog) {
    this.transformRegistry = transformRegistry;
    this.payloadCatalog = payloadCatalog;
  }

  /**
   * 懒流式产出组合实例.
   *
   * @param run 任务（提供 runId + base_attack_types 等元信息）
   * @param dimensions 维度列表（顺序决定生成顺序；id 必须出现在 run.bypassDimensionIds 内）
   * @param assetIds 目标资产 id 列表
   * @return 实例流；caller 应用 limit/peek/forEach 等终结操作消费
   */
  public Stream<CombinationInstance> generate(
      AttackCombinationRun run, List<BypassDimension> dimensions, List<String> assetIds) {
    if (run == null) {
      throw new IllegalArgumentException("run must not be null");
    }
    if (dimensions == null || dimensions.isEmpty()) {
      throw new IllegalArgumentException("dimensions must not be empty");
    }
    if (assetIds == null || assetIds.isEmpty()) {
      throw new IllegalArgumentException("assetIds must not be empty");
    }
    List<String> baseTypes = run.getBaseAttackTypes();
    if (baseTypes == null || baseTypes.isEmpty()) {
      throw new IllegalArgumentException("run.baseAttackTypes must not be empty");
    }

    return baseTypes.stream()
        .flatMap(
            baseType -> {
              String basePayload = payloadCatalog.defaultPayloadFor(baseType);
              return dimensions.stream()
                  .flatMap(
                      dim -> {
                        String transformed = applyTransform(dim, basePayload);
                        String sample = CombinationInstance.truncatePayload(transformed);
                        String combinationId =
                            CombinationInstance.buildCombinationId(baseType, dim.getId());
                        return assetIds.stream()
                            .map(
                                assetId ->
                                    new CombinationInstance(
                                        run.getId(),
                                        combinationId,
                                        baseType,
                                        dim.getId(),
                                        assetId,
                                        transformed,
                                        sample));
                      });
            });
  }

  private String applyTransform(BypassDimension dim, String basePayload) {
    Map<String, Object> config = dim.getTransformConfig();
    return transformRegistry.transform(
        dim.getTransformType(),
        basePayload,
        config == null ? Map.of() : config);
  }
}
