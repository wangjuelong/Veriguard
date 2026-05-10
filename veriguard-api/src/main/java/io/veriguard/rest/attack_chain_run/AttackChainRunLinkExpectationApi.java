package io.veriguard.rest.attack_chain_run;

import io.veriguard.aop.LogExecutionTime;
import io.veriguard.aop.RBAC;
import io.veriguard.attackchain.execution.LinkExpectationService;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.ResourceType;
import io.veriguard.rest.attack_chain_run.form.AttackChainLinkExpectationOutput;
import io.veriguard.rest.helper.RestBehavior;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链路级 SOC DETECTION expectation 查询端点（PRD §2.4 / spec §4.6）.
 *
 * <p>纯只读 —— 实例化 / 评估走 {@code AttackChainNodesExecutionJob} 调度路径。前端运行画布 `LinkExpectationPanel`
 * 通过本端点拉当前 run 的 link expectation 列表（含 traces 命中追溯）。
 */
@RequiredArgsConstructor
@RestController
public class AttackChainRunLinkExpectationApi extends RestBehavior {

  private final LinkExpectationService linkExpectationService;

  @LogExecutionTime
  @GetMapping(value = "/api/attack_chain_runs/{attackChainRunId}/link_expectations")
  @RBAC(
      resourceId = "#attackChainRunId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ATTACK_CHAIN_RUN)
  public List<AttackChainLinkExpectationOutput> attackChainRunLinkExpectations(
      @PathVariable @NotBlank final String attackChainRunId) {
    return this.linkExpectationService.findByRun(attackChainRunId).stream()
        .map(AttackChainLinkExpectationOutput::from)
        .toList();
  }
}
