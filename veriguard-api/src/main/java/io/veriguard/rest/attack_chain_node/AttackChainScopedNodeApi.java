package io.veriguard.rest.attack_chain_node;

import static io.veriguard.database.specification.AttackChainNodeSpecification.fromAttackChain;
import static io.veriguard.rest.attack_chain.AttackChainApi.SCENARIO_URI;
import static io.veriguard.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.veriguard.aop.RBAC;
import io.veriguard.database.model.*;
import io.veriguard.database.repository.*;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeInput;
import io.veriguard.rest.attack_chain_node.form.AttackChainNodeUpdateActivationInput;
import io.veriguard.rest.attack_chain_node.output.AttackChainNodeOutput;
import io.veriguard.rest.attack_chain_node.service.AttackChainScopedNodeService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeDuplicateService;
import io.veriguard.rest.attack_chain_node.service.AttackChainNodeService;
import io.veriguard.rest.helper.RestBehavior;
import io.veriguard.service.*;
import io.veriguard.service.attack_chain.AttackChainService;
import io.veriguard.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Join;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AttackChainScopedNodeApi extends RestBehavior {

  private final AttackChainNodeSearchService attackChainNodeSearchService;
  private final AttackChainNodeRepository attackChainNodeRepository;
  private final AttackChainService attackChainService;
  private final AttackChainNodeService attackChainNodeService;
  private final AttackChainNodeDuplicateService attackChainNodeDuplicateService;
  private final AttackChainScopedNodeService attackChainScopedNodeService;

  // -- READ --

  @GetMapping(SCENARIO_URI + "/{attackChainId}/injects/simple")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Iterable<AttackChainNodeOutput> attackChainScopedNodesSimple(
      @PathVariable @NotBlank final String attackChainId) {
    return attackChainNodeSearchService.attackChainNodes(fromAttackChain(attackChainId));
  }

  @PostMapping(SCENARIO_URI + "/{attackChainId}/injects/simple")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Transactional(readOnly = true)
  public Iterable<AttackChainNodeOutput> attackChainScopedNodesSimple(
      @PathVariable @NotBlank final String attackChainId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<AttackChainNode> specification,
            Specification<AttackChainNode> specificationCount,
            Pageable pageable) ->
            this.attackChainNodeSearchService.attackChainNodes(
                fromAttackChain(attackChainId).and(specification),
                fromAttackChain(attackChainId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        AttackChainNode.class,
        joinMap);
  }

  @GetMapping(SCENARIO_URI + "/{attackChainId}/injects")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<AttackChainNode> attackChainScopedNodes(
      @PathVariable @NotBlank final String attackChainId) {
    return this.attackChainNodeRepository.findByAttackChainId(attackChainId).stream()
        .sorted(AttackChainNode.executionComparator)
        .toList();
  }

  @GetMapping(SCENARIO_URI + "/{attackChainId}/injects/{attackChainNodeId}")
  @RBAC(
      resourceId = "#attackChainNodeId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public AttackChainNode attackChainScopedNode(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String attackChainNodeId) {
    return attackChainScopedNodeService.findAttackChainNodeForAttackChain(
        attackChainId, attackChainNodeId);
  }

  // -- CREATE --

  @PostMapping(SCENARIO_URI + "/{attackChainId}/injects")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public AttackChainNode createAttackChainNodeForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody AttackChainNodeInput input) {
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    return this.attackChainNodeService.createAndSaveAttackChainNode(null, attackChain, input);
  }

  @PostMapping(SCENARIO_URI + "/{attackChainId}/injects/bulk")
  @RBAC(
      resourceId = "#attackChainId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public List<AttackChainNode> createAttackChainNodesForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @Valid @RequestBody List<AttackChainNodeInput> inputs) {
    AttackChain attackChain = this.attackChainService.attackChain(attackChainId);
    return this.attackChainNodeService.createAndSaveAttackChainNodeList(null, attackChain, inputs);
  }

  @PostMapping(SCENARIO_URI + "/{attackChainId}/injects/{attackChainNodeId}")
  @RBAC(
      resourceId = "#attackChainNodeId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode duplicateAttackChainNodeForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String attackChainNodeId) {
    return attackChainNodeDuplicateService
        .duplicateAttackChainNodeForAttackChainWithDuplicateWordInTitle(
            attackChainId, attackChainNodeId);
  }

  // -- UPDATE --

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(SCENARIO_URI + "/{attackChainId}/injects/{attackChainNodeId}")
  @RBAC(
      resourceId = "#attackChainNodeId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode updateAttackChainNodeForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String attackChainNodeId,
      @Valid @RequestBody @NotNull AttackChainNodeInput input) {
    return attackChainScopedNodeService.updateAttackChainNodeForAttackChain(
        attackChainId, attackChainNodeId, input);
  }

  @PutMapping(SCENARIO_URI + "/{attackChainId}/injects/{attackChainNodeId}/activation")
  @RBAC(
      resourceId = "#attackChainNodeId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public AttackChainNode updateAttackChainNodeActivationForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String attackChainNodeId,
      @Valid @RequestBody AttackChainNodeUpdateActivationInput input) {
    return attackChainScopedNodeService.updateAttackChainNodeActivationForAttackChain(
        attackChainId, attackChainNodeId, input);
  }

  // -- DELETE --

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(SCENARIO_URI + "/{attackChainId}/injects/{attackChainNodeId}")
  @RBAC(
      resourceId = "#attackChainNodeId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void deleteAttackChainNodeForAttackChain(
      @PathVariable @NotBlank final String attackChainId,
      @PathVariable @NotBlank final String attackChainNodeId) {
    this.attackChainScopedNodeService.deleteAttackChainNode(attackChainId, attackChainNodeId);
  }
}
