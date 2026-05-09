package io.veriguard.attackchain.execution;

import io.veriguard.database.model.AttackChainLinkExpectation;
import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.AttackChainRun;
import io.veriguard.database.model.LinkExpectationStatus;
import io.veriguard.database.model.LinkVerdict;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 链路级 verdict 计算器（PRD §2.4 / spec §3.5 计数法）.
 *
 * <p>对一条 {@link AttackChainRun} 全链路所有节点的 PREVENTION / DETECTION expectations 聚合：分母 = 该 维度全部
 * expectation 数；分子 = {@link EXPECTATION_STATUS#SUCCESS} 的 expectation 数（PREVENTION SUCCESS =
 * 被防御工具拦下；DETECTION SUCCESS = 被告警系统检出）。两个维度独立计算 {@link LinkVerdict}：
 *
 * <ul>
 *   <li>分母 = 0 → {@link LinkVerdict#N_A}（该维度未配置 expectation）
 *   <li>分子 = 0 → {@link LinkVerdict#FULL_BREACH}（攻击全成功 / 全未检出，攻击方胜）
 *   <li>分子 = 分母 → {@link LinkVerdict#FULL_BLOCKED}（全部被拦下 / 全部被检出，防守方胜）
 *   <li>否则 → {@link LinkVerdict#PARTIAL}
 * </ul>
 *
 * <p>纯函数（无 DB / 无副作用）；调用方在 run 进入终态时调用 {@link #compute(AttackChainRun)} 并把结果 写回 {@link
 * AttackChainRun#setVerdictPrevention} / {@link AttackChainRun#setVerdictDetection}.
 */
@Component
public class LinkVerdictCalculator {

  /**
   * 计算单条 run 的 PREVENTION / DETECTION 维度 verdict。
   *
   * @param run 已加载 {@code attackChainNodes} + 每个 node 的 {@code expectations} 的 run（调用方负责 fetch）
   * @return 两维 verdict 组成的 record；若 run 为 null 返回两个 N_A
   */
  public LinkVerdictResult compute(AttackChainRun run) {
    return compute(run, List.of());
  }

  /**
   * 扩展形态：DETECTION 维度同时纳入链路级 expectation（spec §3.5 "节点级 + 链路级 都计入 DETECTION"）.
   *
   * @param linkExpectations run 的所有 {@link AttackChainLinkExpectation}（调用方负责 fetch）；空 / null
   *     等价于退化到只看节点级
   */
  public LinkVerdictResult compute(
      AttackChainRun run, List<AttackChainLinkExpectation> linkExpectations) {
    if (run == null) {
      return new LinkVerdictResult(LinkVerdict.N_A, LinkVerdict.N_A);
    }
    VerdictStats prevention = collectStats(run, EXPECTATION_TYPE.PREVENTION);
    VerdictStats detection = collectStats(run, EXPECTATION_TYPE.DETECTION);
    if (linkExpectations != null) {
      for (AttackChainLinkExpectation link : linkExpectations) {
        // PENDING 还没结算的视作 0 命中（spec §3.6 expired 后变 UNKNOWN，跟节点级 PENDING/UNKNOWN 同等地占
        // 分母不占分子）。SUCCESS 算分子。FAILED / PARTIAL / UNKNOWN 都只占分母。
        detection = detection.plusOne(link.getStatus() == LinkExpectationStatus.SUCCESS);
      }
    }
    return new LinkVerdictResult(prevention.toVerdict(), detection.toVerdict());
  }

  private VerdictStats collectStats(AttackChainRun run, EXPECTATION_TYPE type) {
    List<AttackChainNode> nodes = run.getAttackChainNodes();
    if (nodes == null || nodes.isEmpty()) {
      return new VerdictStats(0, 0);
    }
    int denominator = 0;
    int numerator = 0;
    for (AttackChainNode node : nodes) {
      List<AttackChainNodeExpectation> expectations = node.getExpectations();
      if (expectations == null) {
        continue;
      }
      for (AttackChainNodeExpectation e : expectations) {
        if (e.getType() != type) {
          continue;
        }
        denominator++;
        if (e.getResponse() == EXPECTATION_STATUS.SUCCESS) {
          numerator++;
        }
      }
    }
    return new VerdictStats(denominator, numerator);
  }

  /**
   * 计数法统计结果：分母 = 该维度 expectation 总数；分子 = SUCCESS 的数量（防守方胜）。
   *
   * <p>{@link #toVerdict()} 应用 spec §3.5 的判定逻辑。
   */
  public record VerdictStats(int denominator, int numerator) {
    public LinkVerdict toVerdict() {
      if (denominator == 0) {
        return LinkVerdict.N_A;
      }
      if (numerator == 0) {
        return LinkVerdict.FULL_BREACH;
      }
      if (numerator == denominator) {
        return LinkVerdict.FULL_BLOCKED;
      }
      return LinkVerdict.PARTIAL;
    }

    /** 累加一条 expectation：分母 +1，命中（SUCCESS）才 +1 分子。 */
    public VerdictStats plusOne(boolean success) {
      return new VerdictStats(denominator + 1, numerator + (success ? 1 : 0));
    }
  }

  /** PREVENTION + DETECTION 维度组合结果。 */
  public record LinkVerdictResult(LinkVerdict prevention, LinkVerdict detection) {}
}
