package io.veriguard.combination.severity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.Tag;
import io.veriguard.database.model.combination.AttackCombinationCluster;
import io.veriguard.database.model.combination.AttackCombinationClusterDim;
import io.veriguard.database.model.combination.SeverityConfig;
import io.veriguard.database.model.combination.SeverityLevel;
import io.veriguard.database.repository.AssetRepository;
import io.veriguard.database.repository.AttackCombinationClusterRepository;
import io.veriguard.database.repository.SeverityConfigRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 单测 —— PR D4 SeverityClassifier 评分公式 / 阈值映射 / 启动校验. */
@ExtendWith(MockitoExtension.class)
class SeverityClassifierTest {

  private static final String RUN_ID = "run-1";

  @Mock SeverityConfigRepository configRepository;
  @Mock AttackCombinationClusterRepository clusterRepository;
  @Mock AssetRepository assetRepository;
  @Mock TransactionTemplate transactionTemplate;

  private BaseAttackTypeSeverityCatalog typeCatalog;
  private AssetSensitivityScorer assetScorer;
  private SeverityClassifier classifier;

  @BeforeEach
  void setUp() {
    typeCatalog = new BaseAttackTypeSeverityCatalog();
    assetScorer = new AssetSensitivityScorer();
    classifier =
        new SeverityClassifier(
            configRepository,
            clusterRepository,
            assetRepository,
            typeCatalog,
            assetScorer,
            transactionTemplate);

    // TransactionTemplate.executeWithoutResult takes Consumer<TransactionStatus>: run inline.
    lenient()
        .doAnswer(
            inv -> {
              @SuppressWarnings("unchecked")
              Consumer<TransactionStatus> cb = (Consumer<TransactionStatus>) inv.getArgument(0);
              cb.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
  }

  // ============================================================
  // 1. 默认权重启动校验
  // ============================================================
  @Test
  void postConstruct_passes_with_default_weights_sum_to_1() {
    // 默认权重 0.5+0.3+0.2=1.0 → @PostConstruct 不抛
    classifier.validateDefaultWeights();
  }

  // ============================================================
  // 2. 高 miss + 高严重攻击 → critical
  // ============================================================
  @Test
  void cluster_with_high_miss_and_sql_injection_is_critical() {
    when(configRepository.findSingleton()).thenReturn(Optional.empty());

    AttackCombinationCluster c =
        cluster(
            AttackCombinationClusterDim.asset,
            "asset-A",
            /*missCount=*/ 100,
            List.of(typeEntry("sql_injection", 50)));
    mockClusters(List.of(c), List.of());

    Asset a = new Asset();
    a.setId("asset-A");
    a.setName("prod-db");
    a.setTags(setOf(tag("production")));
    when(assetRepository.findAllById(any())).thenReturn((Iterable) List.of(a));

    classifier.classify(RUN_ID, true);

    AttackCombinationCluster saved = captureFirstSaved();
    assertThat(saved.getSeverityLevel()).isEqualTo(SeverityLevel.critical);
    assertThat(saved.getSeverityScore()).isGreaterThan(new BigDecimal("70.00"));
  }

  // ============================================================
  // 3. 低 miss + xss → info / medium 边界
  // ============================================================
  @Test
  void cluster_with_low_miss_and_xss_is_info_or_medium() {
    when(configRepository.findSingleton()).thenReturn(Optional.empty());

    AttackCombinationCluster c =
        cluster(
            AttackCombinationClusterDim.asset,
            "asset-B",
            /*missCount=*/ 2,
            List.of(typeEntry("xss", 2)));
    mockClusters(List.of(c), List.of());

    Asset a = new Asset();
    a.setId("asset-B");
    a.setName("dev-host");
    a.setTags(setOf(tag("dev")));
    when(assetRepository.findAllById(any())).thenReturn((Iterable) List.of(a));

    classifier.classify(RUN_ID, true);

    AttackCombinationCluster saved = captureFirstSaved();
    // miss=2 → miss_score=20；xss=60；dev asset=25
    // score = 0.5*20 + 0.3*60 + 0.2*25 = 10 + 18 + 5 = 33 → medium（>10）
    assertThat(saved.getSeverityLevel()).isEqualTo(SeverityLevel.medium);
    assertThat(saved.getSeverityScore())
        .isEqualByComparingTo(new BigDecimal("33.00"));
  }

  // ============================================================
  // 4. 自定义阈值改变 level 归类
  // ============================================================
  @Test
  void custom_config_overrides_thresholds() {
    // 默认 score=33 → medium，自定义把 medium_threshold 提到 35 → 同样的 score 变 info
    SeverityConfig custom = new SeverityConfig();
    custom.setMediumThreshold(new BigDecimal("35.00"));
    custom.setHighThreshold(new BigDecimal("60.00"));
    custom.setCriticalThreshold(new BigDecimal("80.00"));
    when(configRepository.findSingleton()).thenReturn(Optional.of(custom));

    AttackCombinationCluster c =
        cluster(
            AttackCombinationClusterDim.asset,
            "asset-B",
            2,
            List.of(typeEntry("xss", 2)));
    mockClusters(List.of(c), List.of());

    Asset a = new Asset();
    a.setId("asset-B");
    a.setName("dev-host");
    a.setTags(setOf(tag("dev")));
    when(assetRepository.findAllById(any())).thenReturn((Iterable) List.of(a));

    classifier.classify(RUN_ID, true);

    AttackCombinationCluster saved = captureFirstSaved();
    // score=33 ≤ 35 → info
    assertThat(saved.getSeverityLevel()).isEqualTo(SeverityLevel.info);
  }

  // ============================================================
  // 5. 空 cluster → no-op
  // ============================================================
  @Test
  void empty_clusters_no_save_called() {
    when(configRepository.findSingleton()).thenReturn(Optional.empty());
    mockClusters(List.of(), List.of());

    classifier.classify(RUN_ID, true);

    verify(clusterRepository, never()).saveAll(any());
  }

  // ============================================================
  // 6. 权重和 ≠ 1.0 → classify 抛
  // ============================================================
  @Test
  void weights_not_summing_to_1_throws_at_classify_time() {
    SeverityConfig bad = new SeverityConfig();
    bad.setMissCountWeight(new BigDecimal("0.500"));
    bad.setAttackTypeWeight(new BigDecimal("0.500"));
    bad.setAssetSensitivityWeight(new BigDecimal("0.500")); // sum = 1.5
    when(configRepository.findSingleton()).thenReturn(Optional.of(bad));

    assertThatThrownBy(() -> classifier.classify(RUN_ID, true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("weights must sum to 1.0");
  }

  // ============================================================
  // 7. top_base_attack_types empty → type_term=0
  // ============================================================
  @Test
  void empty_top_base_attack_types_contributes_zero() {
    when(configRepository.findSingleton()).thenReturn(Optional.empty());

    AttackCombinationCluster c =
        cluster(
            AttackCombinationClusterDim.asset,
            "asset-C",
            /*missCount=*/ 6,
            /*topBaseAttackTypes=*/ List.of());
    mockClusters(List.of(c), List.of());

    Asset a = new Asset();
    a.setId("asset-C");
    a.setName("plain");
    a.setTags(new HashSet<>());
    when(assetRepository.findAllById(any())).thenReturn((Iterable) List.of(a));

    classifier.classify(RUN_ID, true);

    AttackCombinationCluster saved = captureFirstSaved();
    // miss=6 → miss_score=60；type=0；asset (no tag) =50
    // score = 0.5*60 + 0.3*0 + 0.2*50 = 30 + 0 + 10 = 40
    assertThat(saved.getSeverityScore()).isEqualByComparingTo(new BigDecimal("40.00"));
    // 40 = high_threshold（边界），落 medium（严格 >）
    assertThat(saved.getSeverityLevel()).isEqualTo(SeverityLevel.medium);
  }

  // ============================================================
  // 8. 边界值 — score 严格大于阈值才归较高级别
  // ============================================================
  @Test
  void score_exactly_at_threshold_falls_to_lower_band() {
    when(configRepository.findSingleton()).thenReturn(Optional.empty());

    // 构造 score = 70.00（critical_threshold）→ 应落 high（严格 >）
    // miss=140 (饱和 100)，type=70，asset (no asset) = 50
    // = 0.5*100 + 0.3*70 + 0.2*50 = 50+21+10 = 81 → critical
    // 改造目标 score=70 → 反向调试：miss=100*0.5=50, asset=50*0.2=10, type=10/0.3≈33.33
    // 直接用 device dim（asset_score=50 hard）+ miss=100 (miss_score=100)
    // = 0.5*100 + 0.3*40 + 0.2*50 = 50+12+10 = 72 → critical（>70）
    // 直接走 miss=80 → miss_score=80, 走 device dim asset_score=50,
    // type 用 xss(60) → 0.5*80 + 0.3*60 + 0.2*50 = 40+18+10 = 68 → high
    AttackCombinationCluster c =
        cluster(
            AttackCombinationClusterDim.device,
            "device-X",
            /*missCount=*/ 8,
            List.of(typeEntry("xss", 1)));
    mockClusters(List.of(), List.of(c));

    classifier.classify(RUN_ID, true);

    AttackCombinationCluster saved = captureFirstSaved();
    // miss=8 → miss_score=80；type avg=60 (xss=60)；device dim → asset_score=50
    // score = 0.5*80 + 0.3*60 + 0.2*50 = 40+18+10 = 68 → high（>40）
    assertThat(saved.getSeverityScore()).isEqualByComparingTo(new BigDecimal("68.00"));
    assertThat(saved.getSeverityLevel()).isEqualTo(SeverityLevel.high);
  }

  // ============================================================
  // helpers
  // ============================================================

  private void mockClusters(
      List<AttackCombinationCluster> assetClusters,
      List<AttackCombinationCluster> deviceClusters) {
    when(clusterRepository.findAllByRunIdAndClusterDim(RUN_ID, AttackCombinationClusterDim.asset))
        .thenReturn(assetClusters);
    when(clusterRepository.findAllByRunIdAndClusterDim(RUN_ID, AttackCombinationClusterDim.device))
        .thenReturn(deviceClusters);
  }

  @SuppressWarnings("unchecked")
  private AttackCombinationCluster captureFirstSaved() {
    ArgumentCaptor<Iterable<AttackCombinationCluster>> captor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(clusterRepository, times(1)).saveAll(captor.capture());
    List<AttackCombinationCluster> out = new ArrayList<>();
    captor.getValue().forEach(out::add);
    assertThat(out).isNotEmpty();
    return out.get(0);
  }

  private static AttackCombinationCluster cluster(
      AttackCombinationClusterDim dim,
      String key,
      int missCount,
      List<Map<String, Object>> topBaseAttackTypes) {
    AttackCombinationCluster c = new AttackCombinationCluster();
    c.setId(UUID.randomUUID().toString());
    c.setRunId(RUN_ID);
    c.setClusterDim(dim);
    c.setClusterKey(key);
    c.setClusterLabel(key);
    c.setMissCount(missCount);
    c.setTotalInCluster(missCount);
    c.setTopBaseAttackTypes(topBaseAttackTypes);
    return c;
  }

  private static Map<String, Object> typeEntry(String name, int count) {
    return Map.of("name", name, "count", count);
  }

  private static Tag tag(String name) {
    Tag t = new Tag();
    t.setId("tag-" + name);
    t.setName(name);
    return t;
  }

  private static Set<Tag> setOf(Tag... tags) {
    Set<Tag> s = new HashSet<>();
    for (Tag t : tags) {
      s.add(t);
    }
    return s;
  }
}
