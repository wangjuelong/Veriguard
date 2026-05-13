package io.veriguard.combination.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.Asset;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.combination.AttackCombinationCluster;
import io.veriguard.database.model.combination.AttackCombinationClusterDim;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import io.veriguard.database.model.combination.AttackCombinationResult;
import io.veriguard.database.model.combination.AttackCombinationRun;
import io.veriguard.database.model.combination.AttackCombinationRunStatus;
import io.veriguard.database.model.combination.BypassDimension;
import io.veriguard.database.repository.AssetRepository;
import io.veriguard.database.repository.AttackCombinationClusterRepository;
import io.veriguard.database.repository.AttackCombinationResultRepository;
import io.veriguard.database.repository.AttackCombinationRunRepository;
import io.veriguard.database.repository.BypassDimensionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.util.function.Consumer;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 单测 —— PR D3 ClusterAnalyzer 双维聚类逻辑 + payload top N + 分布统计 + 状态机校验. */
@ExtendWith(MockitoExtension.class)
class ClusterAnalyzerTest {

  private static final String RUN_ID = "run-1";
  private static final String DIM_ENCODING = "dim-encoding";
  private static final String DIM_NOISE = "dim-noise";

  @Mock AttackCombinationRunRepository runRepository;
  @Mock AttackCombinationResultRepository resultRepository;
  @Mock AttackCombinationClusterRepository clusterRepository;
  @Mock BypassDimensionRepository dimensionRepository;
  @Mock AssetRepository assetRepository;
  @Mock TransactionTemplate transactionTemplate;
  @Mock io.veriguard.combination.severity.SeverityClassifier severityClassifier;

  private DeviceKeyExtractor deviceKeyExtractor;
  private ClusterAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    deviceKeyExtractor = new DeviceKeyExtractor();
    analyzer =
        new ClusterAnalyzer(
            runRepository,
            resultRepository,
            clusterRepository,
            dimensionRepository,
            assetRepository,
            deviceKeyExtractor,
            transactionTemplate,
            severityClassifier,
            /*payloadSamplesPerCluster=*/ 5);

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

  @Test
  void throws_when_run_not_completed() {
    AttackCombinationRun run = newRun(AttackCombinationRunStatus.running);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    assertThatThrownBy(() -> analyzer.analyze(RUN_ID, true))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void empty_miss_persists_no_clusters() {
    AttackCombinationRun run = newRun(AttackCombinationRunStatus.completed);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    mockMissPage(List.of());

    analyzer.analyze(RUN_ID, /*overwrite=*/ true);

    verify(clusterRepository).deleteByRunId(RUN_ID);
    verify(clusterRepository, never()).saveAll(any());
  }

  @Test
  void produces_one_asset_cluster_per_asset_with_correct_miss_count() {
    AttackCombinationRun run = newRun(AttackCombinationRunStatus.completed);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

    // 3 assets × miss/hit pairs: asset-A 2 miss, asset-B 1 miss, asset-C 0 miss
    Endpoint a = endpoint("asset-A", "host-a");
    Endpoint b = endpoint("asset-B", "host-b");
    Endpoint c = endpoint("asset-C", "host-c");

    List<AttackCombinationResult> misses =
        List.of(
            miss("asset-A", "sql_injection", DIM_ENCODING, "pA1"),
            miss("asset-A", "xss", DIM_NOISE, "pA2"),
            miss("asset-B", "sql_injection", DIM_ENCODING, "pB1"));
    List<AttackCombinationResult> all =
        new ArrayList<>(misses);
    all.add(hit("asset-A", "sql_injection", DIM_ENCODING, "pAh"));
    all.add(hit("asset-C", "xss", DIM_NOISE, "pCh"));
    mockMissPage(misses);
    mockAllPage(all);
    mockAssets(List.of(a, b, c));
    mockDimensions(List.of(dim(DIM_ENCODING, "encoding.base64"), dim(DIM_NOISE, "noise.space")));

    analyzer.analyze(RUN_ID, true);

    List<AttackCombinationCluster> saved = captureSaved();
    // 2 asset cluster (asset-A, asset-B; asset-C 无 miss 不入 cluster) + 2 device cluster
    List<AttackCombinationCluster> assetClusters =
        saved.stream()
            .filter(cl -> cl.getClusterDim() == AttackCombinationClusterDim.asset)
            .toList();
    assertThat(assetClusters).hasSize(2);

    AttackCombinationCluster ca =
        assetClusters.stream()
            .filter(cl -> cl.getClusterKey().equals("asset-A"))
            .findFirst()
            .orElseThrow();
    assertThat(ca.getMissCount()).isEqualTo(2);
    assertThat(ca.getTotalInCluster()).isEqualTo(3); // 2 miss + 1 hit
    assertThat(ca.getClusterLabel()).isEqualTo(a.getName());

    AttackCombinationCluster cb =
        assetClusters.stream()
            .filter(cl -> cl.getClusterKey().equals("asset-B"))
            .findFirst()
            .orElseThrow();
    assertThat(cb.getMissCount()).isEqualTo(1);
    assertThat(cb.getTotalInCluster()).isEqualTo(1);
  }

  @Test
  void device_cluster_uses_hostname_or_name_fallback() {
    AttackCombinationRun run = newRun(AttackCombinationRunStatus.completed);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));

    Endpoint e1 = endpoint("asset-1", "host-1");
    Endpoint e2 = endpoint("asset-2", "host-2");
    Asset plain = new Asset();
    plain.setId("asset-3");
    plain.setName("plain-asset");

    List<AttackCombinationResult> misses =
        List.of(
            miss("asset-1", "sql_injection", DIM_ENCODING, "p1"),
            miss("asset-2", "sql_injection", DIM_ENCODING, "p2"),
            miss("asset-3", "xss", DIM_NOISE, "p3"));
    mockMissPage(misses);
    mockAllPage(misses);
    mockAssets(List.of(e1, e2, plain));
    mockDimensions(List.of(dim(DIM_ENCODING, "encoding"), dim(DIM_NOISE, "noise")));

    analyzer.analyze(RUN_ID, true);

    List<AttackCombinationCluster> saved = captureSaved();
    List<AttackCombinationCluster> deviceClusters =
        saved.stream()
            .filter(c -> c.getClusterDim() == AttackCombinationClusterDim.device)
            .toList();
    assertThat(deviceClusters).hasSize(3);
    Set<String> keys = new HashSet<>();
    deviceClusters.forEach(c -> keys.add(c.getClusterKey()));
    assertThat(keys).containsExactlyInAnyOrder("host-1", "host-2", "plain-asset");
  }

  @Test
  void payload_samples_capped_at_n_and_deduped() {
    AttackCombinationRun run = newRun(AttackCombinationRunStatus.completed);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    Endpoint a = endpoint("asset-A", "host-a");

    List<AttackCombinationResult> misses = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      misses.add(miss("asset-A", "sql_injection", DIM_ENCODING, "payload-" + i));
    }
    mockMissPage(misses);
    mockAllPage(misses);
    mockAssets(List.of(a));
    mockDimensions(List.of(dim(DIM_ENCODING, "encoding")));

    analyzer.analyze(RUN_ID, true);

    AttackCombinationCluster ca =
        captureSaved().stream()
            .filter(c -> c.getClusterDim() == AttackCombinationClusterDim.asset)
            .findFirst()
            .orElseThrow();
    assertThat(ca.getPayloadSamples()).hasSize(5);
  }

  @Test
  void top_base_attack_types_limited_to_top_5() {
    AttackCombinationRun run = newRun(AttackCombinationRunStatus.completed);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    Endpoint a = endpoint("asset-A", "host-a");

    // 6 different base types each 1 miss → top 5 should be returned, dropping 1.
    List<AttackCombinationResult> misses = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      misses.add(miss("asset-A", "type-" + i, DIM_ENCODING, "p" + i));
    }
    mockMissPage(misses);
    mockAllPage(misses);
    mockAssets(List.of(a));
    mockDimensions(List.of(dim(DIM_ENCODING, "encoding")));

    analyzer.analyze(RUN_ID, true);

    AttackCombinationCluster ca =
        captureSaved().stream()
            .filter(c -> c.getClusterDim() == AttackCombinationClusterDim.asset)
            .findFirst()
            .orElseThrow();
    assertThat(ca.getTopBaseAttackTypes()).hasSize(5);
  }

  @Test
  void top_bypass_dimensions_includes_dim_name_lookup() {
    AttackCombinationRun run = newRun(AttackCombinationRunStatus.completed);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    Endpoint a = endpoint("asset-A", "host-a");

    List<AttackCombinationResult> misses =
        List.of(
            miss("asset-A", "sql_injection", DIM_ENCODING, "p1"),
            miss("asset-A", "sql_injection", DIM_ENCODING, "p2"),
            miss("asset-A", "xss", DIM_NOISE, "p3"));
    mockMissPage(misses);
    mockAllPage(misses);
    mockAssets(List.of(a));
    mockDimensions(
        List.of(dim(DIM_ENCODING, "encoding.base64"), dim(DIM_NOISE, "noise.whitespace")));

    analyzer.analyze(RUN_ID, true);

    AttackCombinationCluster ca =
        captureSaved().stream()
            .filter(c -> c.getClusterDim() == AttackCombinationClusterDim.asset)
            .findFirst()
            .orElseThrow();
    List<Map<String, Object>> top = ca.getTopBypassDimensions();
    assertThat(top).hasSize(2);
    Map<String, Object> first = top.get(0);
    assertThat(first.get("dim_id")).isEqualTo(DIM_ENCODING);
    assertThat(first.get("name")).isEqualTo("encoding.base64");
    assertThat(first.get("count")).isEqualTo(2);
  }

  @Test
  void overwrite_false_skips_delete() {
    AttackCombinationRun run = newRun(AttackCombinationRunStatus.completed);
    when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
    mockMissPage(List.of());

    analyzer.analyze(RUN_ID, /*overwrite=*/ false);

    verify(clusterRepository, never()).deleteByRunId(anyString());
  }

  // ============================================================
  // Helpers
  // ============================================================

  private AttackCombinationRun newRun(AttackCombinationRunStatus status) {
    AttackCombinationRun r = new AttackCombinationRun();
    r.setId(RUN_ID);
    r.setName("run");
    r.setStatus(status);
    r.setExpiresAt(Instant.now().plusSeconds(3600));
    return r;
  }

  private Endpoint endpoint(String id, String hostname) {
    Endpoint e = new Endpoint();
    e.setId(id);
    e.setName("name-of-" + id);
    e.setHostname(hostname);
    return e;
  }

  private AttackCombinationResult miss(
      String assetId, String baseType, String dimId, String payloadSample) {
    return result(assetId, baseType, dimId, payloadSample, AttackCombinationHitState.miss);
  }

  private AttackCombinationResult hit(
      String assetId, String baseType, String dimId, String payloadSample) {
    return result(assetId, baseType, dimId, payloadSample, AttackCombinationHitState.hit);
  }

  private AttackCombinationResult result(
      String assetId,
      String baseType,
      String dimId,
      String payloadSample,
      AttackCombinationHitState state) {
    AttackCombinationResult r = new AttackCombinationResult();
    r.setId(UUID.randomUUID().toString());
    r.setRunId(RUN_ID);
    r.setCombinationId(baseType + ":" + dimId);
    r.setBaseAttackType(baseType);
    r.setBypassDimensionId(dimId);
    r.setAssetId(assetId);
    r.setHitState(state);
    r.setPayloadSample(payloadSample);
    r.setExecutedAt(Instant.now());
    return r;
  }

  private BypassDimension dim(String id, String name) {
    BypassDimension d = new BypassDimension();
    d.setId(id);
    d.setName(name);
    return d;
  }

  private void mockMissPage(List<AttackCombinationResult> items) {
    Page<AttackCombinationResult> page = new PageImpl<>(items);
    when(resultRepository.findAllByRunIdAndHitState(
            eq(RUN_ID), eq(AttackCombinationHitState.miss), any(Pageable.class)))
        .thenReturn(page);
  }

  private void mockAllPage(List<AttackCombinationResult> items) {
    Page<AttackCombinationResult> page = new PageImpl<>(items);
    when(resultRepository.findAllByRunId(eq(RUN_ID), any(Pageable.class))).thenReturn(page);
  }

  private void mockAssets(List<? extends Asset> assets) {
    when(assetRepository.findAllById(any())).thenReturn((Iterable) assets);
  }

  private void mockDimensions(List<BypassDimension> dims) {
    when(dimensionRepository.findAllById(any())).thenReturn(dims);
  }

  @SuppressWarnings("unchecked")
  private List<AttackCombinationCluster> captureSaved() {
    ArgumentCaptor<Iterable<AttackCombinationCluster>> captor =
        ArgumentCaptor.forClass(Iterable.class);
    verify(clusterRepository, times(1)).saveAll(captor.capture());
    List<AttackCombinationCluster> out = new ArrayList<>();
    captor.getValue().forEach(out::add);
    return out;
  }
}
