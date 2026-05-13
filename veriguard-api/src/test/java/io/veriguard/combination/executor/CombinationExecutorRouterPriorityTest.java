package io.veriguard.combination.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.veriguard.combination.CombinationInstance;
import io.veriguard.database.model.combination.AttackCombinationHitState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 优先级测试 —— PR D5 Step 5.
 *
 * <p>验证 {@link HttpInjectExecutor} / {@link PcapReplayExecutor} 注册后路由器优先于
 * {@link StubCombinationExecutor}（route 顺序由 Spring 注入顺序保证，本测用 List 直接模拟）。
 */
class CombinationExecutorRouterPriorityTest {

  private final StubCombinationExecutor stub = new StubCombinationExecutor(0.5, 0.3, 0.2);
  private final HttpInjectExecutor http = new HttpInjectExecutor();
  private final PcapReplayExecutor pcap = new PcapReplayExecutor();

  @Test
  void http_inject_executor_wins_for_web_base_types_over_stub() {
    CombinationExecutorRouter router =
        new CombinationExecutorRouter(List.of(http, pcap, stub), stub);

    for (String base : HttpInjectExecutor.SUPPORTED_BASE_TYPES) {
      CombinationExecutor selected = router.select(base);
      assertThat(selected)
          .as("HttpInjectExecutor should be selected for base_type=%s", base)
          .isSameAs(http);
    }
  }

  @Test
  void pcap_replay_executor_wins_for_pcap_base_types_over_stub() {
    CombinationExecutorRouter router =
        new CombinationExecutorRouter(List.of(http, pcap, stub), stub);

    for (String base : PcapReplayExecutor.SUPPORTED_BASE_TYPES) {
      CombinationExecutor selected = router.select(base);
      assertThat(selected)
          .as("PcapReplayExecutor should be selected for base_type=%s", base)
          .isSameAs(pcap);
    }
  }

  @Test
  void unknown_base_type_falls_back_to_stub() {
    CombinationExecutorRouter router =
        new CombinationExecutorRouter(List.of(http, pcap, stub), stub);

    CombinationExecutor selected = router.select("unknown_base_type");
    assertThat(selected).isSameAs(stub);
  }

  @Test
  void dispatch_with_http_inject_propagates_runtime_exception() {
    // HttpInject 骨架 execute() 抛 UnsupportedOperationException
    // —— 路由器 dispatch 不吞异常，由 caller (Scheduler) 负责重试 / failed 终态.
    CombinationExecutorRouter router =
        new CombinationExecutorRouter(List.of(http, pcap, stub), stub);

    CombinationInstance instance =
        new CombinationInstance("r1", "xss:d1", "xss", "d1", "a1", "<script>", "<script>");
    assertThatThrownBy(() -> router.dispatch(instance))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void stub_only_router_still_handles_all_types_when_http_and_pcap_disabled() {
    // 模拟 @ConditionalOnProperty 未开启场景：容器内只有 stub.
    CombinationExecutorRouter router = new CombinationExecutorRouter(List.of(stub), stub);

    CombinationInstance instance =
        new CombinationInstance(
            "r1", "sql_injection:d1", "sql_injection", "d1", "a1", "p", "p");
    AttackCombinationHitState state = router.dispatch(instance);
    // Stub 概率返 hit/miss/timeout 之一（不抛）
    assertThat(state)
        .isIn(
            AttackCombinationHitState.hit,
            AttackCombinationHitState.miss,
            AttackCombinationHitState.timeout);
  }
}
