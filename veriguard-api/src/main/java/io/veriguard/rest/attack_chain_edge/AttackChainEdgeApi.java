package io.veriguard.rest.attack_chain_edge;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.veriguard.aop.RBAC;
import io.veriguard.database.model.AttackChainEdge;
import io.veriguard.database.model.Action;
import io.veriguard.database.model.EdgeCondition;
import io.veriguard.database.model.ResourceType;
import io.veriguard.database.repository.AttackChainEdgesRepository;
import io.veriguard.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for editing edge conditions (PRD §2.4 / spec §3.4 / §6.3.2).
 *
 * <p>Phase 12b-B3.5b 接入点：前端 ConditionEdgePopover 在 ReactFlow edge `onClick` 后弹出，
 * 用户编辑 sealed 递归 {@link EdgeCondition} 树，提交时 PUT 到这里。
 *
 * <p>边的创建 / 删除沿用 {@link io.veriguard.rest.attack_chain_node.AttackChainNodeApi}
 * 的 inject-update 路径（编辑节点时连带改 dependsOn 列表），本 controller 只负责单条边的
 * 条件更新 —— 与节点级 settings 端点的设计同构（PR #25 B3.5a）。
 */
@RestController
@RequiredArgsConstructor
public class AttackChainEdgeApi {

  public static final String EDGE_URI = "/api/attack_chain_edges";

  private final AttackChainEdgesRepository attackChainEdgesRepository;

  @PutMapping(EDGE_URI + "/{edgeId}/condition")
  @Operation(summary = "Update an attack chain edge condition tree")
  @Transactional
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SCENARIO)
  public AttackChainEdge updateEdgeCondition(
      @PathVariable @NotBlank final String edgeId,
      @RequestBody final EdgeConditionUpdateInput input) {
    AttackChainEdge edge =
        attackChainEdgesRepository
            .findByEdgeId(UUID.fromString(edgeId))
            .orElseThrow(ElementNotFoundException::new);
    edge.setAttackChainEdgeCondition(input == null ? null : input.condition());
    return (AttackChainEdge) attackChainEdgesRepository.save(edge);
  }

  /**
   * Wrapper input form so JSON body has a stable {@code dependency_condition} field, matching the
   * snake_case wire format already used by {@link AttackChainEdge#getAttackChainEdgeCondition()}.
   */
  public record EdgeConditionUpdateInput(@JsonProperty("dependency_condition") EdgeCondition condition) {}
}
