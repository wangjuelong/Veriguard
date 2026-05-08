package io.veriguard.attackchain.execution;

import io.veriguard.database.model.AttackChainNode;
import io.veriguard.database.model.AttackChainNodeExpectation;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_STATUS;
import io.veriguard.database.model.AttackChainNodeExpectation.EXPECTATION_TYPE;
import io.veriguard.database.model.AttackChainRun;
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
    if (run == null) {
      return new LinkVerdictResult(LinkVerdict.N_A, LinkVerdict.N_A);
    }
    VerdictStats prevention = collectStats(run, EXPECTATION_TYPE.PREVENTION);
    VerdictStats detection = collectStats(run, EXPECTATION_TYPE.DETECTION);
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
  }

  /** PREVENTION + DETECTION 维度组合结果。 */
  public record LinkVerdictResult(LinkVerdict prevention, LinkVerdict detection) {}
}
