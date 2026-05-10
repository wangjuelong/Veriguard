package io.veriguard.service.attack_chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.Filters.Filter;
import io.veriguard.database.model.Filters.FilterGroup;
import io.veriguard.database.model.Filters.FilterMode;
import io.veriguard.database.model.Filters.FilterOperator;
import io.veriguard.database.model.NodeContract;
import io.veriguard.database.repository.NodeContractRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link AttackChainService#computeDynamicContracts(AttackChain)}.
 *
 * <p>Mirrors the semantics of {@code AssetGroupService.computeDynamicAssets}: when the chain's
 * {@code dynamicFilter} is empty or null the method short-circuits without hitting the repository;
 * otherwise it builds a JPA Specification from the filter and delegates to {@link
 * NodeContractRepository#findAll(Specification)}. This satisfies the PRD §2.3 第 5 行 "用例更新自动进入"
 * requirement — dynamic contracts are re-queried on every call.
 */
@ExtendWith(MockitoExtension.class)
class AttackChainServiceDynamicContractsTest {

  // Declare @Mocks for all final fields Mockito needs to inject into AttackChainService.
  // We only need NodeContractRepository for this method; the rest are declared so that
  // @InjectMocks can construct the service without NPEs.
  @Mock private io.veriguard.database.repository.AttackChainRepository attackChainRepository;
  @Mock private io.veriguard.database.repository.TeamRepository teamRepository;
  @Mock private io.veriguard.database.repository.UserRepository userRepository;
  @Mock private io.veriguard.database.repository.DocumentRepository documentRepository;

  @Mock
  private io.veriguard.database.repository.AttackChainTeamUserRepository
      attackChainTeamUserRepository;

  @Mock private io.veriguard.utils.mapper.AttackChainRunMapper attackChainRunMapper;
  @Mock private io.veriguard.service.VariableService variableService;
  @Mock private io.veriguard.service.TeamService teamService;
  @Mock private io.veriguard.service.FileService fileService;

  @Mock
  private io.veriguard.rest.attack_chain_node.service.AttackChainNodeDuplicateService
      attackChainNodeDuplicateService;

  @Mock private io.veriguard.service.TagRuleService tagRuleService;

  @Mock
  private io.veriguard.rest.attack_chain_node.service.AttackChainNodeService attackChainNodeService;

  @Mock private io.veriguard.service.UserService userService;

  @Mock
  private io.veriguard.database.repository.AttackChainNodeRepository attackChainNodeRepository;

  @Mock
  private io.veriguard.database.repository.LessonsCategoryRepository lessonsCategoryRepository;

  @Mock private io.veriguard.healthcheck.utils.HealthCheckUtils healthCheckUtils;
  @Mock private io.veriguard.utils.mapper.AttackChainMapper attackChainMapper;

  /** The repository under test — will be verified for call count in short-circuit tests. */
  @Mock private NodeContractRepository nodeContractRepository;

  @InjectMocks private AttackChainService service;

  // ---- helpers ----

  private NodeContract contract(String id) {
    NodeContract c = new NodeContract();
    c.setId(id);
    return c;
  }

  private AttackChain chainWithFilter(FilterGroup fg) {
    AttackChain chain = new AttackChain();
    chain.setDynamicFilter(fg);
    return chain;
  }

  private FilterGroup singleEqFilter(String key, List<String> values) {
    Filter f = Filter.getNewDefaultEqualFilter(key, values);
    return FilterGroup.filterGroupWithFilters(List.of(f));
  }

  // ---- Test 1: defaultFilterGroup (empty filters list) → short-circuit ----

  @Test
  @DisplayName("空 FilterGroup（无 filters）→ 返回空列表，不查 repo")
  void emptyFilterGroup_returnsEmpty_neverCallsRepo() {
    AttackChain chain = chainWithFilter(FilterGroup.defaultFilterGroup());

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).isEmpty();
    verify(nodeContractRepository, never()).findAll(any(Specification.class));
  }

  // ---- Test 2: null dynamicFilter → short-circuit ----

  @Test
  @DisplayName("null dynamicFilter → 返回空列表，不抛异常")
  void nullDynamicFilter_returnsEmpty_noException() {
    AttackChain chain = new AttackChain();
    chain.setDynamicFilter(null);

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).isEmpty();
    verify(nodeContractRepository, never()).findAll(any(Specification.class));
  }

  // ---- Test 3: single eq filter → repo called, result forwarded ----

  @Test
  @DisplayName("单 eq filter（attack_patterns）→ repo 被调用一次，结果透传")
  void singleEqFilter_callsRepoOnce_returnsResult() {
    NodeContract c1 = contract(UUID.randomUUID().toString());
    AttackChain chain =
        chainWithFilter(singleEqFilter("node_contract_attack_patterns", List.of("T1059")));
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of(c1));

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).containsExactly(c1);
    verify(nodeContractRepository).findAll(any(Specification.class));
  }

  // ---- Test 4: multi-filter AND mode → repo called once, result forwarded ----

  @Test
  @DisplayName("AND 模式多 filter（attack_pattern + kill_chain_phase）→ repo 调用一次")
  void multiFilterAndMode_callsRepoOnce() {
    NodeContract c1 = contract(UUID.randomUUID().toString());

    Filter f1 = Filter.getNewDefaultEqualFilter("node_contract_attack_patterns", List.of("T1059"));
    Filter f2 =
        Filter.getNewDefaultEqualFilter("node_contract_kill_chain_phases", List.of("execution"));
    FilterGroup fg = new FilterGroup();
    fg.setMode(FilterMode.and);
    fg.setFilters(List.of(f1, f2));

    AttackChain chain = chainWithFilter(fg);
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of(c1));

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).containsExactly(c1);
    verify(nodeContractRepository).findAll(any(Specification.class));
  }

  // ---- Test 5: multi-filter OR mode → repo called once, both results returned ----

  @Test
  @DisplayName("OR 模式两个 attack_pattern filter → 结果包含 c1 和 c2（任意顺序）")
  void multiFilterOrMode_returnsBothResults() {
    NodeContract c1 = contract(UUID.randomUUID().toString());
    NodeContract c2 = contract(UUID.randomUUID().toString());

    Filter f1 = Filter.getNewDefaultEqualFilter("node_contract_attack_patterns", List.of("T1059"));
    Filter f2 = Filter.getNewDefaultEqualFilter("node_contract_attack_patterns", List.of("T1086"));
    FilterGroup fg = new FilterGroup();
    fg.setMode(FilterMode.or);
    fg.setFilters(List.of(f1, f2));

    AttackChain chain = chainWithFilter(fg);
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of(c1, c2));

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).hasSize(2).contains(c1, c2);
    verify(nodeContractRepository).findAll(any(Specification.class));
  }

  // ---- Test 6: filter present but repo returns empty list ----

  @Test
  @DisplayName("filter 有效但 repo 返回空集合 → 空列表，不抛异常")
  void filterPresentRepoReturnsEmpty_returnsEmptyList() {
    AttackChain chain =
        chainWithFilter(singleEqFilter("node_contract_attack_patterns", List.of("T9999")));
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of());

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).isEmpty();
    verify(nodeContractRepository).findAll(any(Specification.class));
  }

  // ---- Test 7: not_empty operator ----

  @Test
  @DisplayName("not_empty operator → repo 返回 [c1, c2]，结果有 2 项")
  void notEmptyOperator_returnsResults() {
    NodeContract c1 = contract(UUID.randomUUID().toString());
    NodeContract c2 = contract(UUID.randomUUID().toString());

    Filter f = new Filter();
    f.setKey("node_contract_attack_patterns");
    f.setOperator(FilterOperator.not_empty);
    f.setMode(FilterMode.and);
    f.setValues(List.of());
    FilterGroup fg = FilterGroup.filterGroupWithFilters(List.of(f));

    AttackChain chain = chainWithFilter(fg);
    when(nodeContractRepository.findAll(any(Specification.class))).thenReturn(List.of(c1, c2));

    List<NodeContract> result = service.computeDynamicContracts(chain);

    assertThat(result).hasSize(2).contains(c1, c2);
    verify(nodeContractRepository).findAll(any(Specification.class));
  }

  // ---- Test 8: repo throws RuntimeException → propagates ----

  @Test
  @DisplayName("repo.findAll 抛 RuntimeException → 异常透传给调用方")
  void repoThrowsRuntimeException_propagatesToCaller() {
    AttackChain chain =
        chainWithFilter(singleEqFilter("node_contract_attack_patterns", List.of("T1059")));
    when(nodeContractRepository.findAll(any(Specification.class)))
        .thenThrow(new RuntimeException("db error"));

    assertThrows(RuntimeException.class, () -> service.computeDynamicContracts(chain));
    verify(nodeContractRepository).findAll(any(Specification.class));
  }
}
