package io.veriguard.combination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.combination.transform.IdentityTransform;
import io.veriguard.combination.transform.PayloadTransformRegistry;
// PayloadTransform import via FQN in anonymous class
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.model.combination.BypassDimensionCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttackCombinationGeneratorTest {

  private AttackCombinationGenerator generator;
  private BaseAttackPayloadCatalog catalog;

  @BeforeEach
  void setUp() {
    catalog = new BaseAttackPayloadCatalog();
    PayloadTransformRegistry registry =
        new PayloadTransformRegistry(List.of(new IdentityTransform()));
    generator = new AttackCombinationGenerator(registry, catalog);
  }

  @Test
  void cartesian_product_30_instances_3_bases_5_dims_2_assets() {
    AttackCombinationRun run = newRun(List.of("sql_injection", "xss", "rce"));
    List<BypassDimension> dims = newDims(5);
    List<String> assetIds = List.of("asset-A", "asset-B");

    List<CombinationInstance> instances =
        generator.generate(run, dims, assetIds).toList();

    assertThat(instances).hasSize(3 * 5 * 2);
    // 唯一性校验：(combinationId, assetId) 组合不重复
    long distinct =
        instances.stream()
            .map(i -> i.combinationId() + "|" + i.assetId())
            .distinct()
            .count();
    assertThat(distinct).isEqualTo(30);
  }

  @Test
  void generator_is_lazy_unconsumed_combinations_not_evaluated() {
    AtomicInteger transformCalls = new AtomicInteger(0);
    io.veriguard.combination.transform.PayloadTransform counting =
        new io.veriguard.combination.transform.PayloadTransform() {
          @Override
          public String type() {
            return "identity";
          }

          @Override
          public String apply(String base, Map<String, Object> config) {
            transformCalls.incrementAndGet();
            return base;
          }
        };
    PayloadTransformRegistry registry = new PayloadTransformRegistry(List.of(counting));
    AttackCombinationGenerator lazyGenerator = new AttackCombinationGenerator(registry, catalog);

    AttackCombinationRun run = newRun(List.of("sql_injection", "xss"));
    List<BypassDimension> dims = newDims(10);
    List<String> assetIds = List.of("asset-A", "asset-B", "asset-C");

    Stream<CombinationInstance> stream = lazyGenerator.generate(run, dims, assetIds);
    // Generator computes transform once per (base, dim) pair, then multiplexes across assets.
    // Taking 5 instances: base="sql_injection" needs dim0 (3 assets) + dim1 (2 of 3 assets) → 2 transforms.
    List<CombinationInstance> firstFive = stream.limit(5).toList();
    assertThat(firstFive).hasSize(5);
    // ≤ 2 dim transforms suffice for first 5; assert lazy (not all 20 base × dim pairs ran)
    assertThat(transformCalls.get()).isLessThanOrEqualTo(2);
  }

  @Test
  void combination_id_format_is_base_type_colon_dim_id() {
    AttackCombinationRun run = newRun(List.of("sql_injection"));
    List<BypassDimension> dims = newDims(1);
    List<String> assetIds = List.of("asset-X");
    CombinationInstance first = generator.generate(run, dims, assetIds).findFirst().orElseThrow();
    assertThat(first.combinationId()).isEqualTo("sql_injection:" + dims.get(0).getId());
    assertThat(first.runId()).isEqualTo(run.getId());
    assertThat(first.baseAttackType()).isEqualTo("sql_injection");
    assertThat(first.assetId()).isEqualTo("asset-X");
  }

  @Test
  void payload_sample_truncated_at_1024_chars() {
    String longBase = "A".repeat(2000);
    String sample = CombinationInstance.truncatePayload(longBase);
    assertThat(sample).hasSize(1024);
  }

  @Test
  void unknown_base_attack_type_fail_fast() {
    AttackCombinationRun run = newRun(List.of("unknown_type"));
    List<BypassDimension> dims = newDims(1);
    List<String> assetIds = List.of("asset-X");
    assertThatThrownBy(() -> generator.generate(run, dims, assetIds).toList())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown base_attack_type");
  }

  @Test
  void empty_dimensions_throws() {
    AttackCombinationRun run = newRun(List.of("sql_injection"));
    assertThatThrownBy(() -> generator.generate(run, List.of(), List.of("asset-X")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("dimensions");
  }

  @Test
  void empty_asset_ids_throws() {
    AttackCombinationRun run = newRun(List.of("sql_injection"));
    assertThatThrownBy(() -> generator.generate(run, newDims(1), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("assetIds");
  }

  private AttackCombinationRun newRun(List<String> baseTypes) {
    AttackCombinationRun run = new AttackCombinationRun();
    run.setId(UUID.randomUUID().toString());
    run.setName("test-run");
    run.setBaseAttackTypes(new ArrayList<>(baseTypes));
    return run;
  }

  private List<BypassDimension> newDims(int count) {
    List<BypassDimension> out = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      BypassDimension d = new BypassDimension();
      d.setId("dim-" + i);
      d.setName("dim-" + i);
      d.setCategory(BypassDimensionCategory.encoding);
      d.setTransformType("identity");
      d.setTransformConfig(new HashMap<>());
      out.add(d);
    }
    return out;
  }
}
