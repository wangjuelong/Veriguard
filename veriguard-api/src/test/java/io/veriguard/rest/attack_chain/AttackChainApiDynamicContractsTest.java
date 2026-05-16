package io.veriguard.rest.attack_chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.veriguard.database.model.AttackChain;
import io.veriguard.database.model.NodeContract;
import io.veriguard.service.attack_chain.AttackChainService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * §6.3 IPv6 安全验证系统招标 "用例更新自动进入" —— GET /api/attack_chains/{id}/dynamic_contracts 端点 wiring 单测.
 *
 * <p>聚焦 controller 调用链：先 attackChain(id) 取 chain，再 computeDynamicContracts(chain) 现算列表透传。 真实匹配语义已在
 * {@code AttackChainServiceDynamicContractsTest} 覆盖；本测试只验证 endpoint 不把 chain 缓存、每次都现调用 service ——
 * 这是 "用例更新自动进入" 的关键保证。
 */
class AttackChainApiDynamicContractsTest {

  private AttackChainService attackChainService;
  private AttackChainApi api;

  @BeforeEach
  void setUp() {
    attackChainService = Mockito.mock(AttackChainService.class);
    // All other deps unused by this endpoint — pass null. @RequiredArgsConstructor positional.
    api =
        new AttackChainApi(
            null, // customDashboardService
            null, // tagRepository
            null, // teamRepository
            null, // userRepository
            null, // attackChainRepository
            null, // attackChainToAttackChainRunService
            null, // importService
            attackChainService,
            null, // teamService
            null, // assetGroupService
            null, // endpointService
            null, // documentService
            null // platformSettingsService
            );
  }

  @Test
  @DisplayName("returns computed contracts and delegates to service")
  void returns_contracts_from_service() {
    AttackChain chain = new AttackChain();
    chain.setId("chain-1");
    NodeContract c1 = new NodeContract();
    c1.setId("contract-1");
    NodeContract c2 = new NodeContract();
    c2.setId("contract-2");
    when(attackChainService.attackChain("chain-1")).thenReturn(chain);
    when(attackChainService.computeDynamicContracts(chain)).thenReturn(List.of(c1, c2));

    List<NodeContract> result = api.attackChainDynamicContracts("chain-1");

    assertThat(result)
        .hasSize(2)
        .extracting(NodeContract::getId)
        .containsExactly("contract-1", "contract-2");
    verify(attackChainService, times(1)).attackChain("chain-1");
    verify(attackChainService, times(1)).computeDynamicContracts(chain);
  }

  @Test
  @DisplayName("empty filter / no matches → empty list")
  void empty_filter_returns_empty_list() {
    AttackChain chain = new AttackChain();
    chain.setId("chain-empty");
    when(attackChainService.attackChain("chain-empty")).thenReturn(chain);
    when(attackChainService.computeDynamicContracts(chain)).thenReturn(List.of());

    List<NodeContract> result = api.attackChainDynamicContracts("chain-empty");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("两次调用之间用例集合变化（模拟用例新增）→ 端点返回最新集合，无缓存")
  void successive_calls_reflect_payload_changes() {
    AttackChain chain = new AttackChain();
    chain.setId("chain-dynamic");
    NodeContract before = new NodeContract();
    before.setId("contract-before");
    NodeContract after1 = new NodeContract();
    after1.setId("contract-before");
    NodeContract after2 = new NodeContract();
    after2.setId("contract-newly-added");

    when(attackChainService.attackChain("chain-dynamic")).thenReturn(chain);
    when(attackChainService.computeDynamicContracts(chain))
        .thenReturn(List.of(before))
        .thenReturn(List.of(after1, after2));

    List<NodeContract> first = api.attackChainDynamicContracts("chain-dynamic");
    List<NodeContract> second = api.attackChainDynamicContracts("chain-dynamic");

    assertThat(first).hasSize(1);
    assertThat(second).hasSize(2);
    assertThat(second).extracting(NodeContract::getId).contains("contract-newly-added");
    verify(attackChainService, times(2)).computeDynamicContracts(chain);
  }
}
