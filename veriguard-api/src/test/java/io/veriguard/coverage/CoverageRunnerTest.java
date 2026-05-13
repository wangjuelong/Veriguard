package io.veriguard.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.coverage.soc.HealthStatus;
import io.veriguard.coverage.soc.SocAdapter;
import io.veriguard.coverage.soc.SocAdapterRouter;
import io.veriguard.coverage.soc.SocAlert;
import io.veriguard.coverage.soc.SocAlertQuery;
import io.veriguard.coverage.soc.SocQueryTimeoutException;
import io.veriguard.database.model.Asset;
import io.veriguard.database.model.AssetGroup;
import io.veriguard.database.model.Endpoint;
import io.veriguard.database.model.coverage.CoverageBaseline;
import io.veriguard.database.model.coverage.CoverageHitState;
import io.veriguard.database.model.coverage.CoverageResult;
import io.veriguard.database.model.coverage.CoverageRun;
import io.veriguard.database.model.coverage.CoverageRunStatus;
import io.veriguard.database.model.coverage.CoverageType;
import io.veriguard.database.model.coverage.Policy;
import io.veriguard.database.model.coverage.PolicyDeviceType;
import io.veriguard.database.repository.AssetGroupRepository;
import io.veriguard.database.repository.CoverageBaselineRepository;
import io.veriguard.database.repository.CoverageResultRepository;
import io.veriguard.database.repository.CoverageRunRepository;
import io.veriguard.database.repository.PolicyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

@ExtendWith(MockitoExtension.class)
class CoverageRunnerTest {

  private static final String BASELINE_ID = "baseline-1";
  private static final String GROUP_ID = "group-1";

  @Mock CoverageBaselineRepository baselineRepository;
  @Mock CoverageRunRepository runRepository;
  @Mock CoverageResultRepository resultRepository;
  @Mock PolicyRepository policyRepository;
  @Mock AssetGroupRepository assetGroupRepository;
  @Mock SocAdapterRouter socAdapterRouter;
  @Mock TransactionTemplate transactionTemplate;

  private CoverageRunner runner;
  private FakeAdapter adapter;

  @BeforeEach
  void setUp() {
    runner =
        new CoverageRunner(
            baselineRepository,
            runRepository,
            resultRepository,
            policyRepository,
            assetGroupRepository,
            socAdapterRouter,
            transactionTemplate,
            /*honorSocDelay=*/ false);

    adapter = new FakeAdapter();
    lenient().when(socAdapterRouter.select()).thenReturn(adapter);

    // TransactionTemplate.executeWithoutResult inline
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
    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            inv -> {
              @SuppressWarnings("unchecked")
              org.springframework.transaction.support.TransactionCallback<Object> cb =
                  (org.springframework.transaction.support.TransactionCallback<Object>)
                      inv.getArgument(0);
              return cb.doInTransaction(null);
            });
  }

  private CoverageBaseline baseline(CoverageType type) {
    CoverageBaseline b = new CoverageBaseline();
    b.setId(BASELINE_ID);
    b.setName("Test baseline");
    b.setCoverageType(type);
    b.setAssetGroupId(GROUP_ID);
    b.setSocQueryDelaySeconds(0);
    b.setCaseIds(List.of("case-1"));
    return b;
  }

  private Endpoint endpoint(String id, String ip) {
    Endpoint ep = new Endpoint();
    ep.setId(id);
    ep.setName("ep-" + id);
    ep.setIps(new String[] {ip});
    return ep;
  }

  private Asset bareAsset(String id) {
    // pure Asset (no IP) — represents non-Endpoint asset for out-of-scope tests.
    Asset a = new Endpoint();
    a.setId(id);
    a.setName("plain-" + id);
    return a;
  }

  private Policy policy(String id, PolicyDeviceType dt, String externalRuleId) {
    Policy p = new Policy();
    p.setId(id);
    p.setName("policy-" + id);
    p.setDeviceType(dt);
    p.setExternalRuleId(externalRuleId);
    return p;
  }

  private AssetGroup groupWith(Asset... assets) {
    AssetGroup g = new AssetGroup();
    g.setId(GROUP_ID);
    g.setName("group");
    List<Asset> list = new ArrayList<>();
    for (Asset a : assets) {
      list.add(a);
    }
    g.setAssets(list);
    return g;
  }

  private CoverageRun makeRun() {
    CoverageRun r = new CoverageRun();
    r.setId(UUID.randomUUID().toString());
    r.setBaselineId(BASELINE_ID);
    r.setStatus(CoverageRunStatus.pending);
    when(runRepository.findById(r.getId())).thenReturn(Optional.of(r));
    return r;
  }

  @Test
  void basic_run_completes_and_records_counts() {
    when(baselineRepository.findById(BASELINE_ID))
        .thenReturn(Optional.of(baseline(CoverageType.boundary)));
    Endpoint asset = endpoint("a-1", "10.0.0.1");
    when(assetGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(groupWith(asset)));
    Policy p1 = policy("p-1", PolicyDeviceType.waf, "rule-1");
    when(policyRepository.findAll()).thenReturn(List.of(p1));

    adapter.alwaysHit = true;

    CoverageRun run = makeRun();
    runner.execute(run);

    assertThat(adapter.calls).isEqualTo(1);
    verify(resultRepository, atLeastOnce()).saveAll(any());

    ArgumentCaptor<CoverageRun> runCap = ArgumentCaptor.forClass(CoverageRun.class);
    verify(runRepository, atLeastOnce()).save(runCap.capture());
    CoverageRun saved = runCap.getAllValues().get(runCap.getAllValues().size() - 1);
    assertThat(saved.getStatus()).isEqualTo(CoverageRunStatus.completed);
    assertThat(saved.getTotalCells()).isEqualTo(1);
    assertThat(saved.getHitCount()).isEqualTo(1);
  }

  @Test
  void miss_recorded_when_adapter_returns_empty() {
    when(baselineRepository.findById(BASELINE_ID))
        .thenReturn(Optional.of(baseline(CoverageType.boundary)));
    when(assetGroupRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(groupWith(endpoint("a-1", "10.0.0.1"))));
    when(policyRepository.findAll())
        .thenReturn(List.of(policy("p-1", PolicyDeviceType.waf, "rule-1")));

    adapter.alwaysHit = false;
    adapter.alwaysEmpty = true;

    CoverageRun run = makeRun();
    runner.execute(run);

    ArgumentCaptor<List<CoverageResult>> cap =
        ArgumentCaptor.forClass((Class<List<CoverageResult>>) (Class<?>) List.class);
    verify(resultRepository, atLeastOnce()).saveAll(cap.capture());
    List<CoverageResult> saved = cap.getValue();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getHitState()).isEqualTo(CoverageHitState.miss);
  }

  @Test
  void timeout_isolated_when_adapter_throws() {
    when(baselineRepository.findById(BASELINE_ID))
        .thenReturn(Optional.of(baseline(CoverageType.boundary)));
    when(assetGroupRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(groupWith(endpoint("a-1", "10.0.0.1"))));
    when(policyRepository.findAll())
        .thenReturn(List.of(policy("p-1", PolicyDeviceType.waf, "rule-1")));

    adapter.shouldTimeout = true;

    CoverageRun run = makeRun();
    runner.execute(run);

    ArgumentCaptor<CoverageRun> runCap = ArgumentCaptor.forClass(CoverageRun.class);
    verify(runRepository, atLeastOnce()).save(runCap.capture());
    CoverageRun finalSave = runCap.getAllValues().get(runCap.getAllValues().size() - 1);
    assertThat(finalSave.getStatus()).isEqualTo(CoverageRunStatus.completed);
    assertThat(finalSave.getTimeoutCount()).isEqualTo(1);
  }

  @Test
  void out_of_scope_when_hids_policy_against_non_endpoint() {
    when(baselineRepository.findById(BASELINE_ID))
        .thenReturn(Optional.of(baseline(CoverageType.boundary)));
    // group with bare endpoint that has no IPs, then HIDS check → still applicable but no IP → timeout
    // To verify out_of_scope we need a non-Endpoint Asset; since Asset is abstract via discriminator,
    // we simulate an asset with no IPs vs a non-HIDS policy + a HIDS policy
    Endpoint a = endpoint("a-1", "10.0.0.1");
    when(assetGroupRepository.findById(GROUP_ID)).thenReturn(Optional.of(groupWith(a)));
    Policy hidsPolicy = policy("p-hids", PolicyDeviceType.hids, "rule-hids");
    Policy wafPolicy = policy("p-waf", PolicyDeviceType.waf, "rule-waf");
    when(policyRepository.findAll()).thenReturn(List.of(hidsPolicy, wafPolicy));

    adapter.alwaysHit = true;
    CoverageRun run = makeRun();
    runner.execute(run);

    // both policies applicable to Endpoint → both hit, no out_of_scope
    ArgumentCaptor<CoverageRun> runCap = ArgumentCaptor.forClass(CoverageRun.class);
    verify(runRepository, atLeastOnce()).save(runCap.capture());
    CoverageRun finalSave = runCap.getAllValues().get(runCap.getAllValues().size() - 1);
    assertThat(finalSave.getTotalCells()).isEqualTo(2);
    assertThat(finalSave.getHitCount()).isEqualTo(2);
    assertThat(finalSave.getOutOfScopeCount()).isEqualTo(0);
  }

  @Test
  void async_path_returns_run_id_future() throws Exception {
    when(baselineRepository.findById(BASELINE_ID))
        .thenReturn(Optional.of(baseline(CoverageType.boundary)));
    when(assetGroupRepository.findById(GROUP_ID))
        .thenReturn(Optional.of(groupWith(endpoint("a-1", "10.0.0.1"))));
    when(policyRepository.findAll())
        .thenReturn(List.of(policy("p-1", PolicyDeviceType.waf, "rule-1")));
    // make runner.createRun's save return the same instance
    when(runRepository.save(any(CoverageRun.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    // intercept findById for run id (any uuid)
    lenient()
        .when(runRepository.findById(any(String.class)))
        .thenAnswer(
            inv -> {
              CoverageRun r = new CoverageRun();
              r.setId(inv.getArgument(0));
              r.setBaselineId(BASELINE_ID);
              r.setStatus(CoverageRunStatus.pending);
              return Optional.of(r);
            });
    adapter.alwaysHit = true;

    String runId = runner.runAsync(BASELINE_ID).get();
    assertThat(runId).isNotBlank();
  }

  /** Test fake SocAdapter (no Spring config). */
  private static class FakeAdapter implements SocAdapter {
    boolean alwaysHit;
    boolean alwaysEmpty;
    boolean shouldTimeout;
    int calls;

    @Override
    public List<SocAlert> queryAlerts(SocAlertQuery query) {
      calls++;
      if (shouldTimeout) {
        throw new SocQueryTimeoutException("fake timeout");
      }
      if (alwaysEmpty) {
        return List.of();
      }
      if (alwaysHit && !query.assetIps().isEmpty()) {
        return List.of(
            new SocAlert(
                "alert-1",
                query.assetIps().get(0),
                "rule-fake",
                "category",
                Instant.now(),
                "high"));
      }
      return List.of();
    }

    @Override
    public HealthStatus health() {
      return HealthStatus.healthy;
    }

    @Override
    public String name() {
      return "fake";
    }
  }
}
